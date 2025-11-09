package com.ververica.composable_job.devservices;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.testcontainers.redpanda.RedpandaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ApplicationScoped
public class RedpandaDevService {
    
    private static final Logger LOG = Logger.getLogger(RedpandaDevService.class);
    private static RedpandaContainer redpandaContainer;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    
    @ConfigProperty(name = "quarkus.devservices.redpanda.enabled", defaultValue = "true")
    boolean redpandaEnabled;
    
    @ConfigProperty(name = "quarkus.devservices.flink.inventory.enabled", defaultValue = "true")
    boolean inventoryJobEnabled;
    
    void onStart(@Observes StartupEvent ev) {
        if (!ConfigUtils.getProfiles().contains("dev")) {
            LOG.info("Not in dev mode, skipping dev services");
            return;
        }
        
        if (redpandaEnabled) {
            startRedpanda();
            createTopics();
            
            if (inventoryJobEnabled) {
                // Start inventory job after a delay to ensure Redpanda is ready
                CompletableFuture.runAsync(() -> {
                    try {
                        Thread.sleep(5000);
                        startInventoryJob();
                    } catch (Exception e) {
                        LOG.error("Failed to start inventory job", e);
                    }
                }, executorService);
            }
        }
    }
    
    private void startRedpanda() {
        if (redpandaContainer == null || !redpandaContainer.isRunning()) {
            LOG.info("Starting Redpanda container...");
            redpandaContainer = new RedpandaContainer(
                DockerImageName.parse("docker.redpanda.com/redpandadata/redpanda:v24.2.4")
            );
            redpandaContainer.start();
            
            String bootstrapServers = redpandaContainer.getBootstrapServers();
            System.setProperty("kafka.bootstrap.servers", bootstrapServers);
            System.setProperty("mp.messaging.outgoing.ecommerce_events.bootstrap.servers", bootstrapServers);
            System.setProperty("mp.messaging.incoming.ecommerce_processing_fanout.bootstrap.servers", bootstrapServers);
            System.setProperty("mp.messaging.incoming.products-in.bootstrap.servers", bootstrapServers);
            System.setProperty("mp.messaging.outgoing.websocket_fanout.bootstrap.servers", bootstrapServers);
            System.setProperty("mp.messaging.incoming.processing_fanout.bootstrap.servers", bootstrapServers);
            System.setProperty("quarkus.kafka-streams.bootstrap-servers", bootstrapServers);
            
            LOG.infof("Redpanda started at: %s", bootstrapServers);
        }
    }
    
    private void createTopics() {
        try {
            LOG.info("Creating Kafka topics...");
            String[] topics = {
                "ecommerce_events",
                "ecommerce_processing_fanout",
                "products",
                "order_events",
                "inventory_updates",
                "websocket_fanout",
                "processing_fanout"
            };
            
            for (String topic : topics) {
                redpandaContainer.execInContainer(
                    "rpk", "topic", "create", topic, "-p", "3", "-r", "1"
                );
                LOG.infof("Created topic: %s", topic);
            }
        } catch (Exception e) {
            LOG.error("Failed to create topics", e);
        }
    }
    
    private void startInventoryJob() {
        try {
            LOG.info("Inventory Management Flink Job is disabled (runs separately)");
            // NOTE: Flink jobs are run separately during training, not embedded in Quarkus
            // If you need to run the inventory job embedded, uncomment the dependency in build.gradle
            /*
            String bootstrapServers = redpandaContainer.getBootstrapServers();

            // Run the inventory job in a separate thread
            Thread inventoryThread = new Thread(() -> {
                try {
                    System.setProperty("KAFKA_BOOTSTRAP_SERVERS", bootstrapServers);
                    com.ververica.composable_job.flink.inventory.InventoryManagementJob.main(new String[]{});
                } catch (Exception e) {
                    LOG.error("Inventory job failed", e);
                }
            });
            inventoryThread.setDaemon(true);
            inventoryThread.start();

            LOG.info("Inventory Management Job started");
            */
        } catch (Exception e) {
            LOG.error("Failed to start inventory job", e);
        }
    }
}