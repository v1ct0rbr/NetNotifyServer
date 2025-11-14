package br.gov.pb.der.netnotify.model;

import java.util.UUID;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "department")
@lombok.Getter
@lombok.Setter
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "name", nullable = false, unique = true)
    private String name;

    @ManyToOne(optional = true)
    @JoinColumn(
        name = "parent_department_id",
        nullable = true,
        foreignKey = @ForeignKey(name = "fk_department_parent")
    )
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private Department parentDepartment;

}
