package com.masmatix.suite.gym;

import com.masmatix.suite.core.crud.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

@Entity
@Table(name = "gym_member")
public class GymMember extends BaseEntity {

    /** Printed on the member card / key fob and typed or scanned at the desk. */
    @NotBlank
    @Pattern(regexp = "[A-Z0-9-]{3,20}", message = "must be 3 to 20 capital letters, digits or dashes")
    @Column(nullable = false, unique = true, length = 20)
    private String memberCode;

    @NotBlank
    @Size(max = 120)
    @Column(nullable = false, length = 120)
    private String fullName;

    @Size(max = 30)
    @Pattern(regexp = "^[+0-9 ()-]{0,30}$", message = "must be a phone number")
    @Column(length = 30)
    private String phone;

    @Email
    @Size(max = 160)
    @Column(length = 160)
    private String email;

    private Long planId;

    /** Last day the member may enter. */
    private LocalDate expiresOn;

    private boolean frozen;

    public String getMemberCode() { return memberCode; }
    public void setMemberCode(String v) { this.memberCode = v; }
    public String getFullName() { return fullName; }
    public void setFullName(String v) { this.fullName = v; }
    public String getPhone() { return phone; }
    public void setPhone(String v) { this.phone = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public Long getPlanId() { return planId; }
    public void setPlanId(Long v) { this.planId = v; }
    public LocalDate getExpiresOn() { return expiresOn; }
    public void setExpiresOn(LocalDate v) { this.expiresOn = v; }
    public boolean isFrozen() { return frozen; }
    public void setFrozen(boolean v) { this.frozen = v; }
}
