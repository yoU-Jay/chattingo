// shared-libraries/vars/chattingoPipeline.groovy
def call(Map config = [:]) {

    def DOCKERHUB_CREDS = config.get('dockerhubCreds', 'dockerhub-creds')
    def DEPLOY_DIR = config.get('deployDir', '/opt/chattingo_env')

    pipeline {
        agent any

        environment {
            DOCKERHUB_CREDS = "${DOCKERHUB_CREDS}"
            DEPLOY_DIR = "${DEPLOY_DIR}"
            ROLLBACK_TRIGGERED = ''
        } 

        stages {
            stage('Checkout') {
                steps {
                    checkout scm
                    script {
                        SHORT_SHA = sh(returnStdout: true, script: 'git rev-parse --short HEAD').trim()
                        IMAGE_TAG = "${env.BUILD_NUMBER}-${SHORT_SHA}"
                        echo "Using image tag: ${IMAGE_TAG}"
                    }
                }
            }

            stage('Filesystem Scan') {
                steps {
                    sh """
                        echo "Running Trivy Filesystem Scan"
                        trivy fs --exit-code 0 --severity HIGH,CRITICAL .
                    """
                }
            }

            stage('Config Scan') {
                steps {
                    sh """
                        echo "⚙️ Running Trivy Config Scan on docker-compose.yml and Dockerfiles..."
                        trivy config --exit-code 0 --severity HIGH,CRITICAL ./docker-compose.yml
                        trivy config --exit-code 0 --severity HIGH,CRITICAL ./backend/Dockerfile
                        trivy config --exit-code 0 --severity HIGH,CRITICAL ./frontend/Dockerfile
                    """
                }
            }

            stage('Set Repo Names') {
                steps {
                    withCredentials([usernamePassword(credentialsId: "${DOCKERHUB_CREDS}", usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
                        script {
                            env.BACKEND_REPO = "${DH_USER}/chattingo-backend"
                            env.FRONTEND_REPO = "${DH_USER}/chattingo-frontend"
                        }
                    }
                }
            }

            stage('Build Backend Image') {
                steps {
                    sh """
                        docker build -t ${BACKEND_REPO}:${IMAGE_TAG} -t ${BACKEND_REPO}:latest ./backend
                    """
                }
            }

            stage('Build Frontend Image') {
                steps {
                    sh """
                        docker build -t ${FRONTEND_REPO}:${IMAGE_TAG} -t ${FRONTEND_REPO}:latest ./frontend
                    """
                }
            }

            stage('Image Scan') {
                steps {
                    sh """
                        echo "Running Trivy Image Scan"
                        trivy image --exit-code 0 --severity HIGH,CRITICAL ${BACKEND_REPO}:${IMAGE_TAG}
                        trivy image --exit-code 0 --severity HIGH,CRITICAL ${FRONTEND_REPO}:${IMAGE_TAG}
                    """
                }
            }

            stage('Push Images') {
                steps {
                    withCredentials([usernamePassword(credentialsId: "${DOCKERHUB_CREDS}", usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
                        sh """
                            echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
                            docker push ${env.BACKEND_REPO}:${IMAGE_TAG}
                            docker push ${env.BACKEND_REPO}:latest
                            docker push ${env.FRONTEND_REPO}:${IMAGE_TAG}
                            docker push ${env.FRONTEND_REPO}:latest
                            docker logout
                        """
                    }
                }
            }

            stage('Update deploy .env') {
                steps {
                    sh """
                        cp ${DEPLOY_DIR}/.env ${WORKSPACE}/.env
                        cp ${WORKSPACE}/.env ${WORKSPACE}/.env.bak.${BUILD_NUMBER}
                        if grep -q '^BACKEND_TAG=' .env; then
                            sed -i 's|^BACKEND_TAG=.*|BACKEND_TAG=${IMAGE_TAG}|' .env
                        else
                            echo "BACKEND_TAG=${IMAGE_TAG}" >> .env
                        fi
                        if grep -q '^FRONTEND_TAG=' .env; then
                            sed -i 's|^FRONTEND_TAG=.*|FRONTEND_TAG=${IMAGE_TAG}|' .env
                        else
                            echo "FRONTEND_TAG=${IMAGE_TAG}" >> .env
                        fi
                    """
                }
            }

            stage('Deploy (docker compose)') {
                steps {
                    sh """
                        docker compose pull
                        docker compose up -d --remove-orphans
                    """
                }
            }

            stage('Health Check') {
                steps {
                    script {
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            sh """
                                sleep 10
                                curl -f http://localhost:3001
                            """
                            echo "Health check passed ✅"
                            env.ROLLBACK_TRIGGERED = 'false'

                            sh """
                                cp ${WORKSPACE}/.env ${DEPLOY_DIR}/.env.bak
                            """
                            
                        }
                        script {
                            if (currentBuild.currentResult == 'FAILURE') {
                                echo "Health check failed ❌. Triggering rollback..."
                                env.ROLLBACK_TRIGGERED = 'true'
                            }
                        }
                    }
                }
            }

            stage('Rollback') {
                when { expression { env.ROLLBACK_TRIGGERED != 'false' } }
                steps {
                    sh """
                        cp ${DEPLOY_DIR}/.env.bak ${WORKSPACE}/.env
                        docker compose --env-file ${WORKSPACE}/.env pull
                        docker compose --env-file ${WORKSPACE}/.env up -d --remove-orphans
                    """
                    echo "Rollback complete ✅"
                }
            }
        }
        post {
            success {
                echo "Deployment successful: ${IMAGE_TAG}"
            }
            failure {
                echo "Pipeline failed. Check console output."
            }
        }
    }
}

return this
