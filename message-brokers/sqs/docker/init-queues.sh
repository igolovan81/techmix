#!/bin/bash
set -e

echo "=== Creating SQS queues ==="

awslocal sqs create-queue --queue-name simple-queue
awslocal sqs create-queue --queue-name work-queue
awslocal sqs create-queue --queue-name fanout-queue-a
awslocal sqs create-queue --queue-name fanout-queue-b
awslocal sqs create-queue --queue-name pubsub-queue-a
awslocal sqs create-queue --queue-name pubsub-queue-b

# DLQ first, then the source queue that references it
awslocal sqs create-queue --queue-name retry-dlq
awslocal sqs create-queue --queue-name retry-queue \
  --attributes '{
    "VisibilityTimeout": "5",
    "RedrivePolicy": "{\"deadLetterTargetArn\":\"arn:aws:sqs:us-east-1:000000000000:retry-dlq\",\"maxReceiveCount\":\"3\"}"
  }'

awslocal sqs create-queue --queue-name fifo-queue.fifo \
  --attributes '{"FifoQueue":"true","ContentBasedDeduplication":"true"}'

echo "=== Creating SNS topics ==="

awslocal sns create-topic --name fanout-topic
awslocal sns create-topic --name pubsub-topic

echo "=== Subscribing SQS queues to SNS topics ==="

awslocal sns subscribe \
  --topic-arn arn:aws:sns:us-east-1:000000000000:fanout-topic \
  --protocol sqs \
  --notification-endpoint arn:aws:sqs:us-east-1:000000000000:fanout-queue-a \
  --attributes '{"RawMessageDelivery":"true"}'

awslocal sns subscribe \
  --topic-arn arn:aws:sns:us-east-1:000000000000:fanout-topic \
  --protocol sqs \
  --notification-endpoint arn:aws:sqs:us-east-1:000000000000:fanout-queue-b \
  --attributes '{"RawMessageDelivery":"true"}'

awslocal sns subscribe \
  --topic-arn arn:aws:sns:us-east-1:000000000000:pubsub-topic \
  --protocol sqs \
  --notification-endpoint arn:aws:sqs:us-east-1:000000000000:pubsub-queue-a \
  --attributes '{"RawMessageDelivery":"true"}'

awslocal sns subscribe \
  --topic-arn arn:aws:sns:us-east-1:000000000000:pubsub-topic \
  --protocol sqs \
  --notification-endpoint arn:aws:sqs:us-east-1:000000000000:pubsub-queue-b \
  --attributes '{"RawMessageDelivery":"true"}'

echo "=== LocalStack initialization complete ==="
awslocal sqs list-queues
