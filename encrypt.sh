export JASYPT_ENCRYPTOR_PASSWORD="${JASYPT_ENCRYPTOR_PASSWORD:?set JASYPT_ENCRYPTOR_PASSWORD}"
export JASYPT_HTTP_TOKEN="${JASYPT_HTTP_TOKEN:?set JASYPT_HTTP_TOKEN}"

# 암호화 (로컬)
curl -v -i -X  POST http://127.0.0.1:8080/internal/jasypt/encrypt \
  -H "Content-Type: application/json" \
  -H "X-JASYPT-TOKEN: ${JASYPT_HTTP_TOKEN}" \
  -d '{"value":"hello"}' 

# 복호화
# curl -s -X POST http://127.0.0.1:8080/internal/jasypt/decrypt \
#   -H "Content-Type: application/json" \
#   -H "X-JASYPT-TOKEN: ${JASYPT_HTTP_TOKEN}" \
#   -d '{"value":"ENC(...)"}'
# # -> hello
