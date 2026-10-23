package com.masmatix.suite.gym;

import com.masmatix.suite.core.crud.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

/** Every entry attempt at the desk, allowed or not. */
@Entity
@Table(name = "gym_check_in")
public class CheckIn extends BaseEntity {

    private Long memberId;
    @Column(nullable = false, length = 20)
    private String memberCode;
    @Column(length = 120)
    private String memberName;
    @Column(nullable = false)
    private Instant checkedAt;
    private boolean allowed;
    @Column(length = 80)
    private String reason;

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long v) { this.memberId = v; }
    public String getMemberCode() { return memberCode; }
    public void setMemberCode(String v) { this.memberCode = v; }
    public String getMemberName() { return memberName; }
    public void setMemberName(String v) { this.memberName = v; }
    public Instant getCheckedAt() { return checkedAt; }
    public void setCheckedAt(Instant v) { this.checkedAt = v; }
    public boolean isAllowed() { return allowed; }
    public void setAllowed(boolean v) { this.allowed = v; }
    public String getReason() { return reason; }
    public void setReason(String v) { this.reason = v; }
}
