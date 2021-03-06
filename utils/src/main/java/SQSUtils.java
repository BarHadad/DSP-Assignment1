import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import software.amazon.awssdk.utils.CollectionUtils;

import java.util.List;
import java.util.Map;

public class SQSUtils {
    private final static Logger log = LoggerFactory.getLogger(SQSUtils.class);
    private final static SqsClient sqs = SqsClient.builder().region(Region.US_EAST_1).build();

    public static void sendMSG(String qName, String messageBody) {
        buildQueueIfNotExists(qName);
        putMessageInSqs(getQUrl(qName), messageBody);
    }

    public static Message recieveMSG(String qName) {
        return recieveMSG(qName, 0);
    }

    public static Message recieveMSG(String qName, int waitTime) {
        ReceiveMessageRequest receiveRequest = getReceiveMessageRequest(qName, waitTime);
        List<Message> messages = sqs.receiveMessage(receiveRequest).messages();
        return CollectionUtils.isNullOrEmpty(messages) ? null : messages.get(0);
    }

    public static boolean deleteMSG(Message msg, String qName) {
        DeleteMessageRequest deleteRequest = DeleteMessageRequest.builder()
                .queueUrl(getQUrl(qName))
                .receiptHandle(msg.receiptHandle())
                .build();
        sqs.deleteMessage(deleteRequest);
        return true;
    }


    public static String buildQueueIfNotExists(String qName) {
        return buildQueueIfNotExists(qName, null);
    }

    /**
     * @param qName
     * @return Build sqs with the name qName, if not already exists.
     */
    public static String buildQueueIfNotExists(String qName, Map<QueueAttributeName, String> atrributes) {
        try {
            return getQUrl(qName);
            // Throw exception in the first try
        } catch (QueueDoesNotExistException ex) {
            return createQByName(qName, atrributes);
        }
    }

    public static void deleteQ(String qName) {
        DeleteQueueRequest deleteManLocQ = DeleteQueueRequest.builder().queueUrl(getQUrl(qName)).build();
        try {
            sqs.deleteQueue(deleteManLocQ);
        } catch (SqsException ex) {
            log.error("SQSUtils.deleteQ(): error... " + ex.getMessage());
        }
    }

    private static ReceiveMessageRequest getReceiveMessageRequest(String qName, int waitTime) {
        return ReceiveMessageRequest.builder()
                .queueUrl(getQUrl(qName))
                .maxNumberOfMessages(1)
                .waitTimeSeconds(waitTime)
                .build();
    }

    private static String createQByName(String queueName, Map<QueueAttributeName, String> attrMap) {
        CreateQueueRequest request;
        if (!CollectionUtils.isNullOrEmpty(attrMap)) {
            request = CreateQueueRequest.builder()
                    .queueName(queueName)
                    .attributes(attrMap)
                    .build();
        } else {
            request = CreateQueueRequest.builder()
                    .queueName(queueName)
                    .build();
        }
        try {
            CreateQueueResponse create_result = sqs.createQueue(request);
            return create_result.queueUrl();
        } catch (QueueNameExistsException qExistsEx) {
            log.error("SQSUtils.createQByName(): queue with name: " + queueName + " already exists");
        } catch (QueueDeletedRecentlyException ex) {
            log.error("SQSUtils.createQByName(): failed... " + ex.getMessage() + "\nsleeping 1 min and retrying");
            try {
                Thread.sleep(60000);
                return sqs.createQueue(request).queueUrl();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * @param QUEUE_NAME
     * @return this function return the Q url by its name.
     */
    private static String getQUrl(String QUEUE_NAME) throws QueueDoesNotExistException {
        GetQueueUrlRequest getQueueRequest = GetQueueUrlRequest.builder()
                .queueName(QUEUE_NAME)
                .build();
        //get url in order to send later
        return sqs.getQueueUrl(getQueueRequest).queueUrl();
    }

    private static void putMessageInSqs(String queueUrl, String message) {
        SendMessageRequest send_msg_request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(message)
                .delaySeconds(5)
                .build();
        //added for cloud reasons.
        sqs.sendMessage(send_msg_request);
    }
}
