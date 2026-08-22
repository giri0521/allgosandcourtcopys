-- The department book, from the contact sheet the office circulated.
--
-- Two things the sheet carries that V6 had nowhere to put:
--
--   The district. A state department's phonebook is rung across the state, and
--   "Superintendent, Backward Classes Welfare" is a different person in Thoothukudi
--   from the one in Chennai. Nullable, because the taluk book already says where it
--   is and the entries written before this one have no district to give.
--
--   The office. People gave their directorate, corporation or board — TAMIN, TABCEDCO,
--   the Minorities Commission — not one of the 43 secretariat departments a contact
--   hangs from. The department here is the one that office reports to, so the listing
--   groups sensibly, and the office as they wrote it is kept in the designation beside
--   the post, which is how anybody ringing would ask for them anyway.
--
-- Numbers are inserted as the sheet wrote them, spaces and all, for the reason V6
-- gives for not normalising them.

ALTER TABLE phonebook_contacts ADD COLUMN district VARCHAR(120);

INSERT INTO phonebook_contacts (kind, full_name, designation, phone_number, district, department_id)
SELECT 'department', v.full_name, v.designation, v.phone_number, v.district, d.id
FROM (
    VALUES
    ('BALAJI N', 'Record Clerk, Directorate of Minorities Welfare', '9940462607', 'Chennai', 'Department of Backward Classes, Most Backward Classes and Minorities Welfare'),
    ('R. Karthikeyan', 'Assistant, Directorate of Minorities Welfare', '98940 48151', 'Chennai', 'Department of Backward Classes, Most Backward Classes and Minorities Welfare'),
    ('R. Manjula', 'Tahsildar', '9597411145', 'Ariyalur', 'Department of Revenue and Disaster Management'),
    ('K. Kumar', 'Deputy Collector', '9442505250', 'Thanjavur', 'Department of Revenue and Disaster Management'),
    ('Raja K', 'Junior Revenue Inspector', '8608306888', 'Ariyalur', 'Department of Revenue and Disaster Management'),
    ('Sankarraj R', 'Assistant, District Backward Class and Minority Welfare', '7010153470', 'Sivagangai', 'Department of Backward Classes, Most Backward Classes and Minorities Welfare'),
    ('Barath I', 'Junior Revenue Inspector', '9751516812', 'Ariyalur', 'Department of Revenue and Disaster Management'),
    ('Senthilkumar', 'Junior Superintendent, TANSI', '7871176937', 'Chennai', 'Department of Industries'),
    ('N. Ragavendran', 'Upper Division Clerk, Deemed Office Superintendent II, Skill Development and Entrepreneurship', '8144879949', 'Chengalpattu', 'Department of Labour and Employment'),
    ('M. S. Latha', 'Manager (Projects), TABCEDCO', '9444822772', 'Chennai', 'Department of Backward Classes, Most Backward Classes and Minorities Welfare'),
    ('P. Vijayaraghavan', 'Superintendent', '9566767607', 'Chennai', 'Department of Commercial Taxes and Registration'),
    ('Kalaiselvan M', 'Superintendent, Backward Classes Welfare', '9600961761', 'Thoothukudi', 'Department of Backward Classes, Most Backward Classes and Minorities Welfare'),
    ('Radha V', 'Assistant', '7708709961', 'Kancheepuram', 'Department of Backward Classes, Most Backward Classes and Minorities Welfare'),
    ('Rajaji A', 'Typist', '9444542623', 'Chennai', 'Department of Agriculture'),
    ('G. Gunasekaran', 'Private Secretary (Retired), TAMIN', '9600807630', 'Chennai', 'Department of Industries'),
    ('N. Vijayalakshmi', 'Senior Grade Teacher', '9894529939', 'Tiruvallur', 'Department of School Education'),
    ('Bharathi', 'Superintendent', '9994369131', 'Villupuram', 'Department of Backward Classes, Most Backward Classes and Minorities Welfare'),
    ('E. Sumathi', 'Junior Assistant', '9688695622', 'Erode', 'Department of Water Resources'),
    ('Suvathi', 'Junior Assistant, TAMCO', '8825968708', 'Chennai', 'Department of Backward Classes, Most Backward Classes and Minorities Welfare'),
    ('Bhuvaneswari S N', 'PC to Commissioner (Retired), Minorities Welfare', '7010178104', 'Chennai', 'Department of Backward Classes, Most Backward Classes and Minorities Welfare'),
    ('Venkatesan D', 'Senior Revenue Inspector', '9788202040', 'Kancheepuram', 'Department of Revenue and Disaster Management'),
    ('Ramesh Babu M', 'Assistant Director, Local Fund Audit', '9444289611', 'Kancheepuram', 'Department of Finance'),
    ('L. Alamelu', 'Superintendent, Land Reforms', '7904195380', 'Chennai', 'Department of Revenue and Disaster Management'),
    ('S. Suresh', 'Deputy State Tax Officer', '8144998262', 'Kancheepuram', 'Department of Commercial Taxes and Registration'),
    ('J. Ezhilan', 'State Tax Officer', '9842479432', 'Kancheepuram', 'Department of Commercial Taxes and Registration'),
    ('Prabakaran', 'Recovery Supervisor, TAMCO', '7094163867', 'Chennai', 'Department of Backward Classes, Most Backward Classes and Minorities Welfare'),
    ('V. N. Sundar', 'Superintendent, Tamil Nadu State Minorities Commission', '9894608597', 'Chennai', 'Department of Backward Classes, Most Backward Classes and Minorities Welfare'),
    ('P. Durgadevi', 'PC to Commissioner, Directorate of Minorities Welfare', '7639249149', 'Chennai', 'Department of Backward Classes, Most Backward Classes and Minorities Welfare'),
    ('Gunasekaran M', 'Superintendent', '8122833103', 'Kancheepuram', 'Department of Social Welfare and Women Empowerment'),
    ('S. Rajasekaran', 'Assistant Section Officer', '9976962371', 'Cuddalore', 'Department of Miscellaneous Officers, Secretariat'),
    ('M. Vadivelu', 'Section Officer', '8870441444', 'Chennai', 'Department of Finance'),
    ('K. Valarmathi', 'Junior Assistant', '7395977505', 'Chennai', 'Department of Water Resources'),
    ('Mahalakshmi R', 'Junior Assistant, Mining and Monitoring Circle', '6380571950', 'Chennai', 'Department of Water Resources'),
    ('Shathana', 'Assistant, Directorate of Minorities Welfare', '7598582102', 'Chennai', 'Department of Backward Classes, Most Backward Classes and Minorities Welfare'),
    ('L. Pushpalatha', 'Superintendent, Tamil Nadu Construction Workers Welfare Board', '9940042663', 'Chennai', 'Department of Labour and Employment'),
    ('Ajima', 'Assistant, Land Administration', '7358612615', 'Chennai', 'Department of Revenue and Disaster Management'),
    ('R. Lakshmi', 'Manager (C&P), Backward Classes Welfare', '9790856754', 'Chennai', 'Department of Backward Classes, Most Backward Classes and Minorities Welfare'),
    ('Sivakumar', 'PA to Chief Engineer', '9500321110', 'Chennai', 'Department of Water Resources')
) AS v (full_name, designation, phone_number, district, department_name)
JOIN departments d ON d.name = v.department_name;
