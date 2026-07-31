package it.unicas.chronogram.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Personal profile data for a user. Maps the {@code user} table, whose primary
 * key {@code user_auth_user_id} is shared with {@link UserAuth#getUserId()}.
 */
@Entity
@Table(name = "user")
@Getter
@Setter
@NoArgsConstructor
public class UserProfile {

    @Id
    @Column(name = "user_auth_user_id")
    private Integer userId;

    private String name;
    private String surname;
    private String phone;

    @Column(name = "weekly_income")
    private String weeklyIncome;

    @Column(name = "weekly_income_other")
    private String weeklyIncomeOther;

    @Column(name = "weekly_home_cost")
    private String weeklyHomeCost;

    private String notes;
    private String gender;
    private LocalDate birthday;
    private String address;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
