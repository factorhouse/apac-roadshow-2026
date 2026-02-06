import argparse
import logging
import os
from pathlib import Path
from dotenv import load_dotenv

from kafka.admin import KafkaAdminClient, NewTopic
from kafka.errors import TopicAlreadyExistsError, UnknownTopicOrPartitionError

# --- Configure logging ---
logging.basicConfig(
    level=logging.INFO,
    format="[%(asctime)s] %(levelname)s: %(message)s",
)
logger = logging.getLogger(__name__)

# --- Load Environment Variables ---
# The script is in 'scripts/', .env is in the parent directory.
script_dir = Path(__file__).resolve().parent
DOTENV_FILE = script_dir.parent / ".env"
load_dotenv(dotenv_path=DOTENV_FILE)

KAFKA_BOOTSTRAP_SERVERS = os.getenv("KAFKA_BOOTSTRAP_SERVERS")
KAFKA_USER = os.getenv("KAFKA_USER")
KAFKA_PASSWORD = os.getenv("KAFKA_PASSWORD")


def list_topics(admin_client: KafkaAdminClient):
    """
    List all available Kafka topics.
    """
    try:
        topics = admin_client.list_topics()
        if not topics:
            logger.info("No topics found.")
            return

        logger.info(f"Found {len(topics)} topics:")
        for topic in sorted(topics):
            logger.info(f" - {topic}")
        return topics
    except Exception as e:
        logger.error(f"Failed to list topics: {e}")


def create_topics(
    admin_client: KafkaAdminClient,
    topic_names: list,
    num_partitions: int = 1,
    replication_factor: int = 1,
):
    """
    Create multiple Kafka topics.
    """
    existing_topics = admin_client.list_topics()
    topics_to_create = []
    for name in topic_names:
        if name in existing_topics:
            logger.warning(f"Topic '{name}' already exists.")
        else:
            topics_to_create.append(NewTopic(name, num_partitions, replication_factor))

    if not topics_to_create:
        logger.info("No new topics to create.")
        return

    try:
        admin_client.create_topics(new_topics=topics_to_create, validate_only=False)
        for topic in topics_to_create:
            logger.info(f"Topic '{topic.name}' created")
    except TopicAlreadyExistsError as e:
        logger.warning(f"One or more topics already exist: {e}")
    except Exception as e:
        logger.error(f"Failed to create topics: {e}")


def delete_topics(admin_client: KafkaAdminClient, topic_names: list = None):
    """
    Delete Kafka topics.
    If topic_names is None, deletes ALL topics excluding system/provider topics.
    """
    try:
        # If no specific list provided, fetch all and filter
        if topic_names is None:
            logger.info("No specific topics provided. Fetching all topics...")
            all_topics = admin_client.list_topics()

            # Filter out system topics and specific provider topics
            topic_names = [
                n
                for n in all_topics
                if not n.startswith("__") and not n.startswith("instaclustr")
            ]
            logger.info(f"Identified {len(topic_names)} topics for deletion")

        if not topic_names:
            logger.info("No topics found to delete.")
            return

        # Execute deletion
        admin_client.delete_topics(topics=topic_names)

        for topic in topic_names:
            logger.info(f"Topic '{topic}' deletion request sent")

    except UnknownTopicOrPartitionError as e:
        logger.warning(f"One or more topics do not exist: {e}")
    except Exception as e:
        logger.error(f"Failed to delete topics: {e}")


if __name__ == "__main__":
    # Ensure Kafka configuration is loaded
    if not all([KAFKA_BOOTSTRAP_SERVERS, KAFKA_USER, KAFKA_PASSWORD]):
        raise ValueError(
            "Kafka credentials not found. Please set KAFKA_BOOTSTRAP_SERVERS, "
            "KAFKA_USER, and KAFKA_PASSWORD in the .env file of the project root directory."
        )

    parser = argparse.ArgumentParser(
        description="Kafka Admin Client using kafka-python"
    )
    parser.add_argument(
        "--action",
        choices=["create", "delete", "list"],
        default="list",
        help="Action to perform on topics: create, delete or list (default)",
    )
    parser.add_argument(
        "--all",
        action="store_true",
        help="If set with --action delete, deletes all non-system topics.",
    )
    args = parser.parse_args()

    topics = [
        "websocket_fanout",
        "processing_fanout",
        "ecommerce_events",
        "ecommerce_processing_fanout",
        "product-updates",
        "recommendations",
        "inventory_updates",
        "inventory-events",
        "shopping-cart-events",
        "basket-patterns",
        "order-events",
        "product-recommendations",
    ]

    admin_client = None
    try:
        admin_client = KafkaAdminClient(
            bootstrap_servers=KAFKA_BOOTSTRAP_SERVERS.split(","),
            security_protocol="SASL_PLAINTEXT",
            sasl_mechanism="SCRAM-SHA-256",
            sasl_plain_username=KAFKA_USER,
            sasl_plain_password=KAFKA_PASSWORD,
        )

        if args.action == "create":
            create_topics(admin_client, topics)
        elif args.action == "delete":
            # If --all is passed, pass None to delete_topics to trigger the "delete all" logic
            if args.all:
                delete_topics(admin_client, None)
            else:
                delete_topics(admin_client, topics)
        elif args.action == "list":
            list_topics(admin_client)

    except Exception as e:
        logger.error(f"An error occurred: {e}")
    finally:
        if admin_client:
            admin_client.close()
