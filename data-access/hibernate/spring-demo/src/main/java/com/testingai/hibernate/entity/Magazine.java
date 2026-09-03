package com.testingai.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@DiscriminatorValue("MAGAZINE")
@Getter
@Setter
@NoArgsConstructor
public final class Magazine extends LibraryItem {

	@Column(name = "issue_number")
	private String issueNumber;
}
