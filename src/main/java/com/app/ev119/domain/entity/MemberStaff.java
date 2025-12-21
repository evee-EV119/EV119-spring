package com.app.ev119.domain.entity;

import com.app.ev119.domain.type.StaffStatus;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString(exclude = {
        "member",
        "staffCerts"
})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "TBL_MEMBER_STAFF")
@SequenceGenerator(
        name = "SEQ_MEMBER_STAFF_GENERATOR",
        sequenceName = "SEQ_MEMBER_STAFF",
        allocationSize = 1
)
public class MemberStaff {

    @Id @GeneratedValue(strategy = GenerationType.AUTO, generator = "SEQ_MEMBER_STAFF_GENERATOR")
    @EqualsAndHashCode.Include
    private Long id;

    private String memberStaffIndustry;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "MEMBER_ID")
    private Member member;

    @Column(nullable = false, unique = true)
    private String licenseNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StaffStatus staffStatus =  StaffStatus.PENDING;

    @OneToMany(mappedBy = "memberStaff", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<StaffCert> staffCerts;

    {
        staffCerts = new ArrayList<>();
    }
}
