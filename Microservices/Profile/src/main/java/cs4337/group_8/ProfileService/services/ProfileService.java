package cs4337.group_8.ProfileService.services;

import cs4337.group_8.ProfileService.entities.ProfileEntity;
import cs4337.group_8.ProfileService.exceptions.SampleCustomException;
import cs4337.group_8.ProfileService.repositories.ProfileRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class ProfileService {
    private final ProfileRepository ProfileRepository;
    // It is okay to pull in more repositories AND services to use

    public ProfileService(ProfileRepository ProfileRepository) {
        this.ProfileRepository = ProfileRepository;
    }

    // Business logic
    public void sampleMethod() {
        log.info("Sample service method called");
        log.error("Error log: {}", "Some variable goes here");
        // Log a throwable
        log.warn("Warning log:", new SampleCustomException("Some exception goes here"));
    }

    public ProfileEntity getUserExistanceById(String id){
        return ProfileRepository.findById(id).orElseThrow(() -> new SampleCustomException("User not found"));
    }

    public void updateByUserId(
            String userId,
            String fullName,
            String bio,
            String profilePic
    ){
        ProfileRepository.updateByUserId(userId, fullName, bio, profilePic);
    }
}
