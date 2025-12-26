package com.app.ev119.memberInsert;

import com.app.ev119.domain.dto.*;
import com.app.ev119.domain.dto.request.member.SignUpRequestDTO;
import com.app.ev119.domain.entity.*;
import com.app.ev119.domain.type.*;
import com.app.ev119.repository.MemberRepository;
import com.app.ev119.service.*;
import com.app.ev119.service.member.MemberService;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.Commit;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@SpringBootTest @Slf4j
@Commit @Transactional
public class MemberInsertTests {

    @Autowired
    private EntityManager entityManager;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private MemberService memberService;
    @Autowired
    private AllergyService allergyService;
    @Autowired
    private HealthService healthService;
    @Autowired
    private MedicationService medicationService;
    @Autowired
    private EmergencyPhoneService emergencyPhoneService;
    @Autowired
    private VisitedService visitedService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    public void seedMembersWithAllData() throws RuntimeException {
        Long memberId = createMember(123);
        fillAllData(memberId, 1);
        entityManager.flush();
        entityManager.clear();
        changeFirstData(memberId);

        for (int i = 1; i <= 20; i++) {
            memberId = createMember(i);
            fillAllData(memberId, i);
        }
        insertStaff(); // staff@env119.com / staff1234!
        insertAdminAccount(); // admin1@ev119.com / admin1234
    }

//    @Test
//    public void changeFirstDataTest() throws RuntimeException {
//        changeFirstData(memberRepository.findByMemberEmail("test123@gmail.com").orElseThrow().getId());
//    }

//    @Test
    public void insertStaff() {
        Member memberStaff = new Member();
        memberStaff.setMemberEmail("staff@env119.com");
        memberStaff.setMemberPassword(passwordEncoder.encode("staff1234!"));
        memberStaff.setMemberPhone("010" + "1313" + "1313");
        memberStaff.setMemberType(MemberType.STAFF);
        memberStaff.setMemberName("테스트 의료진");

        entityManager.persist(memberStaff);

        MemberStaff memberStaff1 = new MemberStaff();
        memberStaff1.setStaffStatus(StaffStatus.PENDING);
        memberStaff1.setMember(memberStaff);
        memberStaff1.setMemberStaffIndustry("의사");
        memberStaff1.setLicenseNumber("12324794834862394");
        entityManager.persist(memberStaff1);

        entityManager.flush();
        entityManager.clear();

        MemberStaff found = entityManager.find(MemberStaff.class, memberStaff1.getId());
        log.info("의료진 저장: id={}, memberId={}", found.getId(), found.getMember().getId());

    }

//    @Test
    public void insertAdminAccount() {
        log.info("🔥 insertAdminAccount START");

        try {
            Member admin = new Member();
            admin.setMemberName("EV119 관리자");
            admin.setMemberEmail("admin1" + "@ev119.com");
            admin.setMemberPassword(passwordEncoder.encode("admin1234"));
            admin.setMemberPhone("010" + "1234" + "8888"); // 중복 회피

            admin.setMemberType(MemberType.ADMIN);

            entityManager.persist(admin);
            entityManager.flush();

            log.info("🔥 AFTER FLUSH, id={}", admin.getId());
        } catch (Exception e) {
            log.error("❌ flush 실패", e);
            throw e;
        }
    }

    private Long createMember(int i) {
        String email = "test" + i + "@gmail.com";
        String phone = "010" + String.format("%08d", i); // 예: 01000000001

        SignUpRequestDTO dto = new SignUpRequestDTO();
        dto.setMemberEmail(email);
        dto.setMemberPassword("test123!@#");
        dto.setMemberName(i==123? "홍길동" : "user" + i);
        dto.setMemberPhone(phone);

        memberService.signUp(dto);

        return memberRepository.findByMemberEmail(email).orElseThrow().getId();
    }

    private void fillAllData(Long memberId, int i) {
//        2-1. 사용자의 건강정보 수정 (단일)
        HealthDTO health = new HealthDTO();
        health.setMemberId(memberId);
        health.setHealthBloodRh((i % 2 == 0) ? BloodRh.MINUS : BloodRh.PLUS);
        health.setHealthBloodAbo((i % 4 == 0) ? BloodAbo.A : (i % 3 == 0) ? BloodAbo.B : (i % 2 == 0) ? BloodAbo.AB : BloodAbo.O);
        health.setHealthGender((i % 2 == 0) ? GenderType.M : GenderType.W);
        health.setHealthHeight(160.0 + (i - 1));
        health.setHealthWeight(50 + ((i - 1) * 0.5));
        healthService.findHealth(memberId);
        healthService.updateHealth(memberId, health);

//        2-2. 사용자의 건강정보 (기저 질환) 추가 (여러 개)
        String diseaseName = "기저질환" + (i - 1 == 0 ? "" : "_"+(i - 1));
        healthService.addDisease(memberId, diseaseName);
        diseaseName = "천식";
        healthService.addDisease(memberId, diseaseName);

//        3, 사용자의 복용 중인 약물 추가 (여러 개)
        List<MedicationDTO> meds = new ArrayList<>();
        meds.add(makeMedication(memberId, "약-" + i));
        meds.add(makeMedication(memberId, "약-공통"));
        medicationService.findMedications(memberId);
        medicationService.modifyMedications(memberId, meds);

//        4. 사용자의 알러지 리스트 추가 (여러 개)
        List<AllergyDTO> allergies = new ArrayList<>();
        allergies.add(makeAllergy(memberId, (i % 4 == 0) ? AllergyType.MEDICINE : (i % 3 == 0) ? AllergyType.ENVIRONMENT : (i % 2 == 0) ? AllergyType.FOOD : AllergyType.OTHER, "알러지-" + i));
        allergyService.findAllergies(memberId);
        allergyService.modifyAllergies(memberId, allergies);

//        5. 사용자의 응급연락처 리스트 추가 (여러 개)
        List<EmergencyPhoneDTO> phones = new ArrayList<>();
        phones.add(makeEmergency(memberId, "보호자" + i, "가족", "0109" + String.format("%07d", i)));
        phones.add(makeEmergency(memberId, "보호자" + (i + 1), "친구", "0109" + String.format("%07d", i + 1)));
        emergencyPhoneService.findEmergencyPhones(memberId);
        emergencyPhoneService.modifyEmergencyPhones(memberId, phones);

//        6. 사용자의 과거 방문 이력 추가 (여러 개)
        VisitedDTO visitedDTO = new VisitedDTO();
        Date date = null;
        try {
            date = new SimpleDateFormat("yyyy-MM-dd").parse("2025-10-12");
        } catch (ParseException e) {
            date = new Date();
        }
        visitedDTO.setMemberId(memberId);
        visitedDTO.setVisitedDate(date); // Date타입
        visitedDTO.setVisitedName("병원 명");
        visitedDTO.setVisitedDepartment("진료과");
        visitedDTO.setVisitedType(VisitedType.HOSPITAL);
//        visitedDTO.setVisitedType(VisitedType.CLINIC);
//        visitedDTO.setVisitedType(VisitedType.EMERGENCY);
        visitedDTO.setVisitedReason("방문 사유");
        visitedDTO.setVisitedDiagnosis("진단명");
        visitedDTO.setVisitedTreatmentContent("치료 내용");
        visitedDTO.setVisitedDoctor("담당 의사 명");
        visitedService.findVisitedLogs(memberId);
        visitedService.addVisitedLog(memberId, visitedDTO);
    }

    private MedicationDTO makeMedication(Long memberId, String name) {
        MedicationDTO dto = new MedicationDTO();
        dto.setMemberId(memberId);
        dto.setMedicationName(name);
        dto.setMedicationUsage(((memberId - 1) % 7 + 1) +"일 " + ((memberId + 1) % 3 + 1) + "회");
        dto.setMedicationTakingtime("아침 식후");
        return dto;
    }

    private AllergyDTO makeAllergy(Long memberId, AllergyType type, String name) {
        AllergyDTO dto = new AllergyDTO();
        dto.setMemberId(memberId);
        dto.setAllergyType(type);
        dto.setAllergyName(name);
        return dto;
    }

    private EmergencyPhoneDTO makeEmergency(Long memberId, String name, String relationship, String num) {
        EmergencyPhoneDTO dto = new EmergencyPhoneDTO();
        dto.setMemberId(memberId);
        dto.setEmergencyPhoneName(name);
        dto.setEmergencyPhoneRelationship(relationship);
        dto.setEmergencyPhoneNumber(num);
        return dto;
    }

    private void changeFirstData(Long memberId){
        HealthDTO healthDTO = healthService.findHealth(memberId);
        healthDTO.setHealthBloodRh(BloodRh.PLUS);
        healthDTO.setHealthBloodAbo(BloodAbo.B);
        healthDTO.setHealthHeight(169.5);
        healthDTO.setHealthWeight(51.7);
        healthDTO.getDiseases().forEach(diseaseDTO -> {
            healthService.removeDisease(memberId, diseaseDTO);
        });
        healthService.updateHealth(memberId, healthDTO);

        healthService.addDisease(memberId, "척추측만증");
        healthService.addDisease(memberId, "천식");

        List<MedicationDTO> medicationDTOList = medicationService.findMedications(memberId);
        medicationDTOList.get(0).setMedicationName("근육 완화제");
        medicationDTOList.get(0).setMedicationUsage("1일 3회");
        medicationDTOList.get(0).setMedicationTakingtime("식후 30분");
        if(medicationDTOList.size()>1){
            medicationDTOList.get(1).setMedicationName("소염 진통제");
            medicationDTOList.get(1).setMedicationUsage("1일 3회");
            medicationDTOList.get(1).setMedicationTakingtime("식후 30분");
        }
        medicationService.modifyMedications(memberId, medicationDTOList);

        List<AllergyDTO> allergyDTOList = allergyService.findAllergies(memberId);
        allergyDTOList.get(0).setAllergyName("동물 털 알러지");
        allergyDTOList.get(0).setAllergyType(AllergyType.OTHER);
        allergyService.modifyAllergies(memberId, allergyDTOList);

        List<EmergencyPhoneDTO> emergencyPhoneDTOList = emergencyPhoneService.findEmergencyPhones(memberId);
        emergencyPhoneDTOList.get(0).setEmergencyPhoneName("김흥국");
        emergencyPhoneDTOList.get(1).setEmergencyPhoneName("장원영");
        emergencyPhoneService.modifyEmergencyPhones(memberId, emergencyPhoneDTOList);

        List<VisitedDTO> visitedDTOList = visitedService.findVisitedLogs(memberId);
        visitedService.removeVisitedLog(memberId, visitedDTOList.get(0));

        VisitedDTO visitedDTO = new VisitedDTO();
        Date date = null;
        try {
            date = new SimpleDateFormat("yyyy-MM-dd").parse("2025-10-15");
        } catch (ParseException e) {
            date = new Date();
        }
        visitedDTO.setMemberId(memberId);
        visitedDTO.setVisitedDate(date);
        visitedDTO.setVisitedName("마취통증재활치료");
        visitedDTO.setVisitedDepartment("통증의학과");
        visitedDTO.setVisitedType(VisitedType.HOSPITAL);
//        visitedDTO.setVisitedType(VisitedType.CLINIC);
//        visitedDTO.setVisitedType(VisitedType.EMERGENCY);
        visitedDTO.setVisitedReason("허리통증");
        visitedDTO.setVisitedDiagnosis("척추 측만증");
        visitedDTO.setVisitedTreatmentContent("물리치료, 스테로이드 주사");
        visitedDTO.setVisitedDoctor("안유진");
        visitedService.addVisitedLog(memberId, visitedDTO);
    }
}
