package com.allgos.dms.folder.entity;

import com.allgos.dms.common.entity.LowercaseEnumConverter;
import jakarta.persistence.Converter;

/** folders.category is lowercase in the database ('govt_order'), uppercase in Java. */
@Converter(autoApply = true)
public class FolderCategoryConverter extends LowercaseEnumConverter<FolderCategory> {

    public FolderCategoryConverter() {
        super(FolderCategory.class);
    }
}
