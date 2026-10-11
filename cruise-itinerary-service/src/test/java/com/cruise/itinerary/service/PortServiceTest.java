package com.cruise.itinerary.service;

import com.cruise.itinerary.dto.PortRequest;
import com.cruise.itinerary.dto.PortResponse;
import com.cruise.itinerary.entity.Port;
import com.cruise.itinerary.exception.DuplicateResourceException;
import com.cruise.itinerary.exception.ResourceNotFoundException;
import com.cruise.itinerary.repository.PortRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PortService Unit Tests (KAN-105)")
class PortServiceTest {

    @Mock
    private PortRepository portRepository;

    @InjectMocks
    private PortService portService;

    private Port samplePort;
    private PortRequest sampleRequest;

    @BeforeEach
    void setUp() {
        samplePort = Port.builder()
                .name("Cảng Tuần Châu")
                .code("VN-TCH")
                .city("Hạ Long")
                .country("Việt Nam")
                .timezone("Asia/Ho_Chi_Minh")
                .active(true)
                .build();
        samplePort.setId(1L);

        sampleRequest = new PortRequest();
        sampleRequest.setName("Cảng Tuần Châu");
        sampleRequest.setCode("VN-TCH");
        sampleRequest.setCity("Hạ Long");
        sampleRequest.setCountry("Việt Nam");
        sampleRequest.setTimezone("Asia/Ho_Chi_Minh");
        sampleRequest.setActive(true);
    }

    @Test
    @DisplayName("createPort thành công khi thông tin hợp lệ")
    void createPortSuccess() {
        when(portRepository.existsByCodeIgnoreCase("VN-TCH")).thenReturn(false);
        when(portRepository.existsByNameIgnoreCase("Cảng Tuần Châu")).thenReturn(false);
        when(portRepository.save(any(Port.class))).thenReturn(samplePort);

        PortResponse resp = portService.createPort(sampleRequest);

        assertThat(resp).isNotNull();
        assertThat(resp.getCode()).isEqualTo("VN-TCH");
        assertThat(resp.getName()).isEqualTo("Cảng Tuần Châu");
        verify(portRepository).save(any(Port.class));
    }

    @Test
    @DisplayName("createPort ném lỗi DuplicateResourceException khi mã cảng đã tồn tại")
    void createPortDuplicateCode() {
        when(portRepository.existsByCodeIgnoreCase("VN-TCH")).thenReturn(true);

        assertThatThrownBy(() -> portService.createPort(sampleRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Mã cảng 'VN-TCH' đã tồn tại");
    }

    @Test
    @DisplayName("createPort ném lỗi DuplicateResourceException khi tên cảng đã tồn tại")
    void createPortDuplicateName() {
        when(portRepository.existsByCodeIgnoreCase("VN-TCH")).thenReturn(false);
        when(portRepository.existsByNameIgnoreCase("Cảng Tuần Châu")).thenReturn(true);

        assertThatThrownBy(() -> portService.createPort(sampleRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("Tên cảng 'Cảng Tuần Châu' đã tồn tại");
    }

    @Test
    @DisplayName("updatePort thành công khi cập nhật hợp lệ")
    void updatePortSuccess() {
        when(portRepository.findById(1L)).thenReturn(Optional.of(samplePort));
        when(portRepository.existsByCodeIgnoreCaseAndIdNot("VN-TCH", 1L)).thenReturn(false);
        when(portRepository.existsByNameIgnoreCaseAndIdNot("Cảng Tuần Châu", 1L)).thenReturn(false);
        when(portRepository.save(any(Port.class))).thenReturn(samplePort);

        PortResponse resp = portService.updatePort(1L, sampleRequest);

        assertThat(resp).isNotNull();
        assertThat(resp.getCode()).isEqualTo("VN-TCH");
    }

    @Test
    @DisplayName("updatePort ném lỗi khi không tìm thấy cảng")
    void updatePortNotFound() {
        when(portRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> portService.updatePort(99L, sampleRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getPortById thành công")
    void getPortByIdSuccess() {
        when(portRepository.findById(1L)).thenReturn(Optional.of(samplePort));

        PortResponse resp = portService.getPortById(1L);
        assertThat(resp.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getAllPorts và getActivePorts thành công")
    void getAllAndActivePortsSuccess() {
        Pageable pageable = PageRequest.of(0, 10);
        when(portRepository.searchPorts(null, true, pageable))
                .thenReturn(new PageImpl<>(List.of(samplePort)));
        when(portRepository.findByActiveTrue()).thenReturn(List.of(samplePort));

        Page<PortResponse> page = portService.getAllPorts(null, true, pageable);
        List<PortResponse> active = portService.getActivePorts();

        assertThat(page.getContent()).hasSize(1);
        assertThat(active).hasSize(1);
    }

    @Test
    @DisplayName("deletePort thực hiện xóa mềm (active = false)")
    void deletePortSoftDelete() {
        when(portRepository.findById(1L)).thenReturn(Optional.of(samplePort));
        when(portRepository.save(any(Port.class))).thenReturn(samplePort);

        portService.deletePort(1L);

        assertThat(samplePort.getActive()).isFalse();
        verify(portRepository).save(samplePort);
    }
}
