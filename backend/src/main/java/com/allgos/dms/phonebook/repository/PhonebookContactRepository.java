package com.allgos.dms.phonebook.repository;

import com.allgos.dms.phonebook.entity.PhonebookContact;
import com.allgos.dms.phonebook.entity.PhonebookKind;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PhonebookContactRepository extends JpaRepository<PhonebookContact, UUID> {

    /**
     * The department book, read in one query with its departments already resolved.
     *
     * <p>A join fetch rather than a plain findBy: the view names each contact's department, and
     * without this a book of two hundred numbers is two hundred and one queries.
     */
    @Query("""
            select c from PhonebookContact c
            join fetch c.department d
            where c.kind = com.allgos.dms.phonebook.entity.PhonebookKind.DEPARTMENT
            order by d.name asc, c.fullName asc
            """)
    List<PhonebookContact> departmentBook();

    /**
     * The taluk book, by taluk and then by name.
     *
     * <p>Deliberately not ordered by role here. The column holds the converter's lowercase strings,
     * so {@code order by c.role} would sort 'group_member' ahead of 'tahsildar' — the opposite of
     * how the book is read. The service splits the two out, where the enum means what it says.
     */
    @Query("""
            select c from PhonebookContact c
            where c.kind = com.allgos.dms.phonebook.entity.PhonebookKind.TALUK
            order by c.taluk asc, c.fullName asc
            """)
    List<PhonebookContact> talukBook();

    long countByKind(PhonebookKind kind);
}
