package sw1.backend.flowroad.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AwsS3Config {

    @Value("${aws.region:}")
    private String awsRegion;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(resolveRegion())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(resolveRegion())
                .build();
    }

    private Region resolveRegion() {
        if (StringUtils.hasText(awsRegion)) {
            String configuredRegion = awsRegion.trim();

            return Region.regions()
                    .stream()
                    .filter(region -> region.id().equals(configuredRegion))
                    .findFirst()
                    .orElse(Region.US_EAST_1);
        }

        return Region.US_EAST_1;
    }
}
