./gradlew --stop

GRADLE_OPTS='-Xmx3g -XX:MaxMetaspaceSize=1536m' \
./gradlew sonarqube   \
  -Dsonar.projectKey=studio-api \
  -Dsonar.java.source=11 \
  -Dsonar.host.url=http://localhost:9000   \
  -Dsonar.login=sqp_8248e24ea76a0f6ff2fb5b3ae8522dfae765e88e \
  -Pprofile=dev --info --stacktrace

