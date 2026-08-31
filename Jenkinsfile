pipeline {
    agent any

    stages {
        stage('Check Env') {
            steps {
                bat '''
                echo Hello Jenkins
                git --version
                java -version
                '''
            }
        }
    }
}