#!/bin/bash
for room in {1..20}; do
  for partition in {0..2}; do
    QUEUE_NAME="room.${room}.partition.${partition}"
    curl -u guest:guest -X PUT \
      "http://localhost:15672/api/queues/%2f/${QUEUE_NAME}" \
      -H "content-type:application/json" \
      -d '{"durable":true}'
    echo " ✓ $QUEUE_NAME"
  done
done
echo "Done!"
