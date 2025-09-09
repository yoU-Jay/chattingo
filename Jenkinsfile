pipeline {
  agent any

  environment {
    DOCKERHUB_CREDS = 'dockerhub-creds'  // Jenkins credential ID
    BACKEND_REPO = "YOUR_DOCKERHUB_USER/chattingo-backend"
    FRONTEND_REPO = "YOUR_DOCKERHUB_USER/chattingo-frontend"
    DEPLOY_DIR = "/opt/chattingo"        // deployment directory on VPS
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

    stage('Push Images') {
      steps {
        withCredentials([usernamePassword(credentialsId: "${DOCKERHUB_CREDS}", usernameVariable: 'DH_USER', passwordVariable: 'DH_PASS')]) {
          script {
              BACKEND_REPO = "${DH_USER}/chattingo-backend"
              FRONTEND_REPO = "${DH_USER}/chattingo-frontend"
          }
          sh '''
            echo "$DH_PASS" | docker login -u "$DH_USER" --password-stdin
            docker push ${BACKEND_REPO}:${IMAGE_TAG}
            docker push ${BACKEND_REPO}:latest
            docker push ${FRONTEND_REPO}:${IMAGE_TAG}
            docker push ${FRONTEND_REPO}:latest
            docker logout
          '''
        }
      }
    }

    stage('Update deploy .env') {
      steps {
        // modify deploy .env in DEPLOY_DIR (make backup first)
        sh """
          cd ${DEPLOY_DIR}
          cp .env .env.bak.${BUILD_NUMBER}
          # update or add BACKEND_TAG/FRONTEND_TAG (create if not present)
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
          cd ${DEPLOY_DIR}
          docker compose pull
          docker compose up -d --remove-orphans
        """
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
