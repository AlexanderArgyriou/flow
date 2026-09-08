#!/usr/bin/env bash

BASE_URL="http://localhost:9695"

echo "Firing 1000 purchase requests..."

for i in $(seq 1 1000); do
  curl -s -X POST "$BASE_URL/purchases" \
    -H "Content-Type: application/json" \
    -d "{
      \"requester\": \"john$i\",
      \"description\": \"MacBook Pro\",
      \"supplier\": \"Apple\",
      \"total\": 2499.99
    }" > /dev/null &

  echo "Fired purchase $i/1000"
done

echo "All 1000 purchase requests fired."
echo "Waiting 2 minutes..."

sleep 120

echo "Firing 1000 approval requests..."

for i in $(seq 1 1000); do
  if (( RANDOM % 2 == 0 )); then
    decision="APPROVED"
  else
    decision="REJECTED"
  fi

  curl -s -X POST "$BASE_URL/approval" \
    -H "Content-Type: application/json" \
    -d "{
      \"purchaseId\": $i,
      \"decision\": \"$decision\"
    }" > /dev/null &

  echo "Fired approval $i/1000: $decision"
done

echo "All 1000 approval requests fired."