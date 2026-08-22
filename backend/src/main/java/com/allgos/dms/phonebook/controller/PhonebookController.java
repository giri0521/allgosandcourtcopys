package com.allgos.dms.phonebook.controller;

import com.allgos.dms.phonebook.dto.PhonebookResponses.DepartmentContacts;
import com.allgos.dms.phonebook.dto.PhonebookResponses.TalukContacts;
import com.allgos.dms.phonebook.service.PhonebookService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reading the phonebook, which every approved user may do.
 *
 * <p>No filtering by the caller's own department: the point of the book is the office you do not
 * already sit in. Maintenance lives on {@code /admin/phonebook} — see {@code AdminPhonebookController}.
 */
@RestController
@RequestMapping("/api/v1/phonebook")
public class PhonebookController {

    private final PhonebookService phonebookService;

    public PhonebookController(PhonebookService phonebookService) {
        this.phonebookService = phonebookService;
    }

    /** Numbers by department. */
    @GetMapping("/departments")
    public List<DepartmentContacts> departments() {
        return phonebookService.departmentBook();
    }

    /** Tahsildars and group members, by taluk. */
    @GetMapping("/taluks")
    public List<TalukContacts> taluks() {
        return phonebookService.talukBook();
    }
}
