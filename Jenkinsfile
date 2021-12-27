pipeline {
  agent any

  stages {
    stage('Test') {
      steps {
        sh './gradlew test'
      }
    }

    stage('Build') {
      steps {
        sh 'rm -rf build/libs/'
        sh './gradlew build'
      }
    }

    stage('Publish') {
      steps {
        sh './gradlew publish'
      }
    }

    stage('Archive') {
      steps {
        archiveArtifacts(onlyIfSuccessful: true, artifacts: 'build/libs/*')
      }
    }
  }
}
