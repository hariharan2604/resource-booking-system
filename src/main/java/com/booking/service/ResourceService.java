package com.booking.service;

import com.booking.entity.Resource;
import com.booking.dto.ResourceRequest;
import com.booking.dto.ResourceResponse;
import com.booking.exception.ResourceNotFoundException;
import com.booking.repository.ResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ResourceService {

    private final ResourceRepository resourceRepository;

    @Cacheable(value = "resources", key = "#pageable.pageNumber + ':' + #pageable.pageSize + ':' + #pageable.sort.toString()")
    @Transactional(readOnly = true)
    public Page<ResourceResponse> getAll(Pageable pageable) {
        return resourceRepository.findAll(pageable).map(this::toResponse);
    }

    @Cacheable(value = "resources", key = "#id")
    @Transactional(readOnly = true)
    public ResourceResponse getById(Long id) {
        return toResponse(findEntity(id));
    }

    @CachePut(value = "resources", key = "#result.id")
    public ResourceResponse create(ResourceRequest request) {
        Resource resource = Resource.builder()
                .name(request.getName())
                .type(request.getType())
                .description(request.getDescription())
                .location(request.getLocation())
                .capacity(request.getCapacity())
                .pricePerHour(request.getPricePerHour())
                .available(request.getAvailable() == null || request.getAvailable())
                .build();

        return toResponse(resourceRepository.save(resource));
    }

    @CachePut(value = "resources", key = "#id")
    public ResourceResponse update(Long id, ResourceRequest request) {
        Resource resource = findEntity(id);

        resource.setName(request.getName());
        resource.setType(request.getType());
        resource.setDescription(request.getDescription());
        resource.setLocation(request.getLocation());
        resource.setCapacity(request.getCapacity());
        resource.setPricePerHour(request.getPricePerHour());
        if (request.getAvailable() != null) {
            resource.setAvailable(request.getAvailable());
        }

        return toResponse(resourceRepository.save(resource));
    }

    @CacheEvict(value = "resources", key = "#id")
    public void delete(Long id) {
        if (!resourceRepository.existsById(id)) {
            throw new ResourceNotFoundException("Resource not found with id: " + id);
        }
        resourceRepository.deleteById(id);
    }

    Resource findEntity(Long id) {
        return resourceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Resource not found with id: " + id));
    }

    Resource findEntityForUpdate(Long id) {
        Resource resource = resourceRepository.findByIdForUpdate(id);
        if (resource == null) {
            throw new ResourceNotFoundException("Resource not found with id: " + id);
        }
        return resource;
    }

    private ResourceResponse toResponse(Resource r) {
        return ResourceResponse.builder()
                .id(r.getId())
                .name(r.getName())
                .type(r.getType())
                .description(r.getDescription())
                .location(r.getLocation())
                .capacity(r.getCapacity())
                .pricePerHour(r.getPricePerHour())
                .available(r.isAvailable())
                .build();
    }
}
