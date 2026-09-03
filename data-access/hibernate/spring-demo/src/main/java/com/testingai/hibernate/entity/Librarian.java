package com.testingai.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "librarian")
@DiscriminatorValue("LIBRARIAN")
@PrimaryKeyJoinColumn(name = "person_id")
@Getter
@Setter
@NoArgsConstructor
public final class Librarian extends Person {

	@Column(name = "employee_id", nullable = false, unique = true)
	private String employeeId;

	@Column(name = "department", nullable = false)
	private String department;
}
