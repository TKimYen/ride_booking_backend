package com.ridebooking.booking.service;

import com.ridebooking.booking.dto.BusCompanyRequest;
import com.ridebooking.booking.dto.BusCompanyResponse;
import com.ridebooking.booking.entity.BusCompany;
import com.ridebooking.booking.exception.ConflictException;
import com.ridebooking.booking.exception.ResourceNotFoundException;
import com.ridebooking.booking.repository.BusCompanyRepository;
import com.ridebooking.booking.util.LikePattern;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Quản lý danh sách nhà xe. */
@Service
@Transactional(readOnly = true)
public class BusCompanyService {

    static final int SUGGEST_LIMIT = 20;

    private final BusCompanyRepository busCompanyRepository;

    public BusCompanyService(BusCompanyRepository busCompanyRepository) {
        this.busCompanyRepository = busCompanyRepository;
    }

    public List<BusCompanyResponse> suggest(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return listAll();
        }
        return busCompanyRepository
                .suggest(LikePattern.escape(keyword.trim()),
                        PageRequest.of(0, SUGGEST_LIMIT))
                .stream().map(BusCompanyResponse::from).toList();
    }

    public List<BusCompanyResponse> listAll() {
        return busCompanyRepository.findAll().stream()
                .map(BusCompanyResponse::from)
                .toList();
    }

    public BusCompanyResponse getById(Integer id) {
        return BusCompanyResponse.from(requireById(id));
    }

    @Transactional
    public BusCompanyResponse create(BusCompanyRequest request) {
        String name = request.name().trim();
        busCompanyRepository.findByNameIgnoreCase(name).ifPresent(existing -> {
            throw new ConflictException("Nhà xe '" + name + "' đã tồn tại");
        });

        BusCompany company = new BusCompany();
        company.setName(name);
        company.setDescription(request.description());
        company.setLogoUrl(request.logoUrl());
        return BusCompanyResponse.from(busCompanyRepository.save(company));
    }

    @Transactional
    public BusCompanyResponse update(Integer id, BusCompanyRequest request) {
        BusCompany company = requireById(id);
        String name = request.name().trim();
        busCompanyRepository.findByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException("Nhà xe '" + name + "' đã tồn tại");
                });

        company.setName(name);
        company.setDescription(request.description());
        company.setLogoUrl(request.logoUrl());
        return BusCompanyResponse.from(busCompanyRepository.save(company));
    }

    @Transactional
    public void delete(Integer id) {
        busCompanyRepository.delete(requireById(id));
    }

    private BusCompany requireById(Integer id) {
        return busCompanyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhà xe với id " + id));
    }
}
