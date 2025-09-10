def call(Map config = [:]) {

    def DOCKERHUB_CREDS = config.get('dockerhubCreds', 'dockerhub-creds')
    def DEPLOY_DIR = config.get('deployDir', '/opt/chattingo_env')

    pipeline {
        agent any

        environment {
            DOCKERHUB_CREDS = "${DOCKERHUB_CREDS}"
            DEPLOY_DIR = "${DEPLOY_DIR}"
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
                        echo "Running Trivy Config Scan for Dockerfiles"
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

            stage('Deploy - docker compose') {
                steps {
                    sh """
                        docker compose pull
                        #docker compose up -d --remove-orphans
                        docker compose down -v
                    """
                }
            }

            stage('Health Check') {
                steps {
                    script {
                        catchError(buildResult: 'SUCCESS', stageResult: 'FAILURE') {
                            sh """
                                sleep 10
                                curl -f http://localhost:3000
                            """
                            echo "Health check passed !!"
                            env.HEALTH_CHECK_PASS = 'true'

                            sh """
                                cp ${WORKSPACE}/.env ${DEPLOY_DIR}/.env.bak
                            """
                            
                        }
                    }
                }
            }

            stage('Rollback') {
                when { expression { env.HEALTH_CHECK_PASS != 'true' } }
                steps {
                    sh """
                        cp ${DEPLOY_DIR}/.env.bak ${WORKSPACE}/.env
                        docker compose --env-file ${WORKSPACE}/.env pull
                        docker compose --env-file ${WORKSPACE}/.env up -d --remove-orphans
                    """
                    echo "Rollback completed !!"
                }
            }
        }
        post {
            success {
                echo "Deployment successful"
            }
            failure {
                echo "Pipeline failed. Check console output."
            }
        }
    }
}

return this
