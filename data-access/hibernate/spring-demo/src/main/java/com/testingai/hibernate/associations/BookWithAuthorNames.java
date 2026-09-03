package com.testingai.hibernate.associations;

import java.util.List;

public record BookWithAuthorNames(Long id, String title, List<String> authorNames) {
}
