package it.unicas.chronogram.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A single logged activity. Maps the {@code activity} table.
 */
@Entity
@Table(name = "activity")
@Getter
@Setter
@NoArgsConstructor
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_id")
    private Integer id;

    @Column(name = "activity_date", nullable = false)
    private LocalDate activityDate;

    @Column(name = "duration_mins")
    private Integer durationMins;

    private Integer pleasantness;

    @Column(length = 100)
    private String location;

    @Column(name = "cost_euro", precision = 7, scale = 2)
    private BigDecimal costEuro;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    /** Owning activity type; loaded lazily and exposed through the DTO layer. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "activity_type_id", nullable = false)
    private ActivityType activityType;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
