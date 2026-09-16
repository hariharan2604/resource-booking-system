pipeline {
    agent any

    environment {
        IMAGE_NAME = 'hariharan2604/resource-booking'
        IMAGE_TAG  = "${BUILD_NUMBER}"
    }

    stages {

        stage('Build & Test') {
            steps {
                sh '''
                    chmod +x gradlew
                    ./gradlew clean build
                '''
            }
        }

        stage('Code Coverage') {
            steps {
                recordCoverage(
                    tools: [[
                        parser: 'JACOCO',
                        pattern: 'build/reports/jacoco/test/jacocoTestReport.xml'
                    ]],

                    sourceCodeRetention: 'EVERY_BUILD',

                    qualityGates: [
                        [
                            threshold: 50.0,
                            metric: 'LINE',
                            criticality: 'UNSTABLE'
                        ],
                        [
                            threshold: 50.0,
                            metric: 'BRANCH',
                            criticality: 'UNSTABLE'
                        ]
                    ]
                )
            }
        }

        stage('Docker Build') {
            steps {
                script {
                    if (env.BRANCH_NAME == 'master') {
                        sh """
                            docker build \
                                -t ${IMAGE_NAME}:${IMAGE_TAG} \
                                -t ${IMAGE_NAME}:latest \
                                .
                        """
                    } else {
                        sh """
                            docker build \
                                -t ${IMAGE_NAME}:${BRANCH_NAME}-${IMAGE_TAG} \
                                .
                        """
                    }
                }
            }
        }

        stage('Docker Push') {
            steps {
                withCredentials([
                    usernamePassword(
                        credentialsId: 'dockerhub-credentials',
                        usernameVariable: 'DOCKER_USERNAME',
                        passwordVariable: 'DOCKER_PASSWORD'
                    )
                ]) {
                    sh '''
                        echo "$DOCKER_PASSWORD" | docker login \
                            -u "$DOCKER_USERNAME" \
                            --password-stdin

                        if [ "$BRANCH_NAME" = "master" ]; then
                            docker push "${IMAGE_NAME}:${IMAGE_TAG}"
                            docker push "${IMAGE_NAME}:latest"
                        else
                            docker push "${IMAGE_NAME}:${BRANCH_NAME}-${IMAGE_TAG}"
                        fi

                        docker logout
                    '''
                }
            }
        }
    }

    post {
        always {
            junit(
                allowEmptyResults: true,
                testResults: 'build/test-results/test/*.xml'
            )
        }

        success {
            echo "Pipeline completed successfully for ${BRANCH_NAME}"
        }

        failure {
            echo "Pipeline failed for ${BRANCH_NAME}"
        }
    }
}
