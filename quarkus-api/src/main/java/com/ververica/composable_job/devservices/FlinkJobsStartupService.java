package com.ververica.composable_job.devservices;

import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.configuration.ConfigUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class FlinkJobsStartupService {
    
    private static final Logger LOG = Logger.getLogger(FlinkJobsStartupService.class);
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    
    @ConfigProperty(name = "flink.jobs.inventory.enabled", defaultValue = "true")
    boolean inventoryJobEnabled;
    
    @ConfigProperty(name = "kafka.bootstrap.servers", defaultValue = "PLAINTEXT://localhost:19092")
    String kafkaBootstrapServers;
    
    void onStart(@Observes StartupEvent ev) {
        var profiles = ConfigUtils.getProfiles();
        LOG.infof("Starting up with profiles: %s", profiles);

        if (!profiles.contains("dev") && !profiles.contains("test")) {
            LOG.info("Not in dev/test mode, skipping Flink jobs startup");
            return;
        }
        
        // Start Flink jobs after a delay to ensure Kafka is ready
        CompletableFuture.runAsync(() -> {
            try {
                LOG.info("Waiting for Kafka to be ready...");
                TimeUnit.SECONDS.sleep(10);
                
                if (inventoryJobEnabled) {
                    startInventoryJob();
                }
            } catch (Exception e) {
                LOG.error("Failed to start Flink jobs", e);
            }
        }, executorService);
    }
    
    private void startInventoryJob() {
        try {
            LOG.info("Inventory Management Flink Job is disabled (runs separately)");
            LOG.infof("Kafka configured at: %s", kafkaBootstrapServers);
            // NOTE: Flink jobs are run separately during training, not embedded in Quarkus
            // If you need to run the inventory job embedded, uncomment the dependency in build.gradle
            /*
            // Run the inventory job in a separate daemon thread
            Thread inventoryThread = new Thread(() -> {
                try {
                    // Set environment for the Flink job
                    System.setProperty("KAFKA_BOOTSTRAP_SERVERS", kafkaBootstrapServers);

                    // Start the inventory job
                    String[] args = new String[]{};
                    com.ververica.composable_job.flink.inventory.InventoryManagementJob.main(args);
                } catch (Exception e) {
                    LOG.error("Inventory job execution failed", e);
                }
            }, "inventory-flink-job");

            inventoryThread.setDaemon(true);
            inventoryThread.start();

            LOG.info("Inventory Management Job started successfully");
            */
        } catch (Exception e) {
            LOG.error("Failed to start inventory job", e);
        }
    }
}