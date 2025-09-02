export JASYPT_ENCRYPTOR_PASSWORD='mySecret'
export JASYPT_HTTP_TOKEN="local-dev-token"

# 암호화 (로컬)
curl -v -i -X  POST http://127.0.0.1:8080/internal/jasypt/encrypt \
  -H "Content-Type: application/json" \
  -H "X-JASYPT-TOKEN: local-dev-token" \
  -d '{"value":"hello"}' 

# 복호화
# curl -s -X POST http://127.0.0.1:8080/internal/jasypt/decrypt \
#   -H "Content-Type: application/json" \
#   -H "X-JASYPT-TOKEN: local-dev-token" \
#   -d '{"value":"ENC(...)"}'
# # -> hello
