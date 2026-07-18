pipeline {
  agent none
  options {
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 60, unit: 'MINUTES')
  }
  stages {
    stage('Build') {
      agent any
      environment {
        JAVA_HOME = 'C:\\Program Files\\Java\\jdk-26.0.1'
        PATH = "${JAVA_HOME}\\bin;C:\\ProgramData\\chocolatey\\lib\\maven\\apache-maven-3.9.16\\bin;C:\\Program Files\\nodejs;C:\\Program Files\\Git\\cmd;${env.PATH}"
      }
      stages {
        stage('Contract') {
          steps {
            bat 'node scripts\\ci\\check-openapi-contract.mjs'
          }
        }
        stage('Test') {
          steps {
            bat 'mvn -B test'
          }
        }
        stage('Package') {
          steps {
            bat 'mvn -B -DskipTests package'
          }
        }
        stage('Archive') {
          steps {
            archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true, allowEmptyArchive: true
          }
        }
      }
    }
    stage('Integration') {
      when {
        anyOf {
          branch 'main'
          tag pattern: 'v*', comparator: 'GLOB'
        }
      }
      steps {
        build job: 'audio-streaming/integration', wait: true, propagate: true
      }
    }
    stage('Deploy staging') {
      when { branch 'main' }
      steps {
        build job: 'audio-streaming/deploy-staging', wait: true, propagate: true
      }
    }
    stage('Staging smoke') {
      when { branch 'main' }
      steps {
        build job: 'audio-streaming/staging-smoke', wait: true, propagate: true
      }
    }
    stage('Tag release') {
      when { tag pattern: 'v*', comparator: 'GLOB' }
      agent any
      steps {
        echo "Release tag ${env.TAG_NAME}: artifacts archived from Build stage; no staging deploy from tags."
      }
    }
  }
}
