package com.rocketcrew.pocat.domain.user.entity;

import com.rocketcrew.pocat.domain.user.enums.UserRole;
import com.rocketcrew.pocat.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET deleted_at = NOW() WHERE id = ?")
public class User extends BaseEntity {

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "nickname", nullable = false, length = 100)
    private String nickname;

    @Column(name = "phone", length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 20)
    private UserRole userRole;

    @Column(name = "bank_name", length = 50)
    private String bankName;

    @Column(name = "bank_account", length = 50)
    private String bankAccount;

    @Column(name = "billing_key")
    private String billingKey;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "unpaid_strike", nullable = false)
    @Builder.Default
    private int unpaidStrike = 0;

    @Column(name = "is_bid_blocked", nullable = false)
    @Builder.Default
    private boolean isBidBlocked = false;

    public void updateProfile(String nickname, String phone, String address) {
        this.nickname = nickname;
        this.phone = phone;
        this.address = address;
    }

    public void updateBank(String bankName, String bankAccount) {
        this.bankName = bankName;
        this.bankAccount = bankAccount;
    }
}
