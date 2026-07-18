pipeline {
  agent any
  options {
    timestamps()
    buildDiscarder(logRotator(numToKeepStr: '20'))
    timeout(time: 45, unit: 'MINUTES')
  }
  environment {
    JAVA_HOME = 'C:\\Program Files\\Java\\jdk-26.0.1'
    PATH = "${JAVA_HOME}\\bin;C:\\ProgramData\\chocolatey\\lib\\maven\\apache-maven-3.9.16\\bin;C:\\Program Files\\Git\\cmd;${env.PATH}"
  }
  stages {
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
