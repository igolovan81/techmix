package com.testingai.hibernate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorColumn;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "library_item")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "item_type")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
// Not sealed: Hibernate generates a ByteBuddy lazy-load proxy subclass of LibraryItem at runtime
// (Copy.libraryItem is a lazy @ManyToOne to it) and the JVM's sealed/permits check rejects that
// generated class, crashing SessionFactory bootstrap with IncompatibleClassChangeError.
public abstract class LibraryItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@EqualsAndHashCode.Include
	private Long id;

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "publication_year", nullable = false)
	private int publicationYear;

	@OneToMany(mappedBy = "libraryItem", fetch = FetchType.LAZY)
	@BatchSize(size = 10)
	private List<Copy> copies = new ArrayList<>();
}
