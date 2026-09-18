pipeline {
    agent any

    environment {
        IMAGE_NAME = 'hariharan2604/resource-booking'
        IMAGE_TAG  = "${BUILD_NUMBER}"
    }

    stages {

        stage('Verify Environment') {
            steps {
                sh '''
                    echo "Java version:"
                    java -version

                    echo "Gradle version:"
                    gradle --version

                    echo "Docker version:"
                    docker --version
                '''
            }
        }

        stage('Build & Test') {
            steps {
                sh '''
                    gradle clean build --no-daemon
                '''
            }
        }

        stage('SonarQube Analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    withCredentials([
                        string(
                            credentialsId: 'sonarqube-credentials',
                            variable: 'SONAR_TOKEN'
                        )
                    ]) {
                        sh '''
                            gradle sonar \
                                --no-daemon \
                                -Dsonar.token="$SONAR_TOKEN"
                        '''
                    }
                }
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

        stage('Prepare Docker Tag') {
            steps {
                script {
                    if (env.BRANCH_NAME == 'master') {
                        env.DOCKER_TAG = env.IMAGE_TAG
                    } else {
                        // Convert branch name into a valid Docker tag
                        // feature/auth -> feature-auth
                        // bugfix/JIRA-123 -> bugfix-JIRA-123
                        // release/v1.0 -> release-v1.0
                        env.SAFE_BRANCH_NAME = env.BRANCH_NAME
                            .replaceAll(/[^a-zA-Z0-9_.-]/, '-')
                            .replaceAll(/-+/, '-')
                            .replaceAll(/^-+|-+$/, '')

                        env.DOCKER_TAG = "${env.SAFE_BRANCH_NAME}-${env.IMAGE_TAG}"
                    }

                    echo "Branch: ${env.BRANCH_NAME}"
                    echo "Docker tag: ${env.DOCKER_TAG}"
                }
            }
        }

        stage('Docker Build') {
            steps {
                sh '''
                    if [ "$BRANCH_NAME" = "master" ]; then
                        docker build \
                            -t "${IMAGE_NAME}:${IMAGE_TAG}" \
                            -t "${IMAGE_NAME}:latest" \
                            .
                    else
                        docker build \
                            -t "${IMAGE_NAME}:${DOCKER_TAG}" \
                            .
                    fi
                '''
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
                            docker push "${IMAGE_NAME}:${DOCKER_TAG}"
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