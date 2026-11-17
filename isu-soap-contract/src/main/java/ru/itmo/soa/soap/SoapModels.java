package ru.itmo.soa.soap;

import java.time.ZonedDateTime;
import java.time.OffsetDateTime;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import ru.itmo.soa.model.ApiModels;

/** Wire-format conversion only; no validation or business operations. */
public final class SoapModels {
    private SoapModels() {}

    private static XMLGregorianCalendar date(String value) {
        try { return DatatypeFactory.newInstance().newXMLGregorianCalendar(value); }
        catch (javax.xml.datatype.DatatypeConfigurationException failure) { throw new IllegalStateException(failure); }
    }

    public static StudyGroup toSoap(ApiModels.StudyGroup source) {
        StudyGroup result = new StudyGroup();
        result.setId(source.getId()); result.setCreationDate(date(source.getCreationDate().toString()));
        result.setName(source.getName()); result.setStudentsCount(source.getStudentsCount());
        result.setFormOfEducation(source.getFormOfEducation().name());
        result.setSemesterEnum(source.getSemesterEnum() == null ? null : source.getSemesterEnum().name());
        Coordinates coordinates = new Coordinates();
        coordinates.setX(source.getCoordinates().getX()); coordinates.setY(source.getCoordinates().getY());
        result.setCoordinates(coordinates);
        ApiModels.Person admin = source.getGroupAdmin();
        if (admin != null) {
            Person person = new Person(); person.setName(admin.getName()); person.setNationality(admin.getNationality().name());
            person.setHairColor(admin.getHairColor() == null ? null : admin.getHairColor().name());
            person.setBirthday(admin.getBirthday() == null ? null : date(admin.getBirthday().toOffsetDateTime().toString()));
            if (admin.getLocation() != null) {
                Location location = new Location(); location.setX(admin.getLocation().getX());
                location.setY(admin.getLocation().getY()); location.setZ(admin.getLocation().getZ()); person.setLocation(location);
            }
            result.setGroupAdmin(person);
        }
        return result;
    }

    public static ApiModels.StudyGroup toRest(StudyGroup source) {
        Person admin = source.getGroupAdmin();
        ApiModels.Person person = admin == null ? null : new ApiModels.Person(admin.getName(),
                admin.getBirthday() == null ? null : ZonedDateTime.parse(admin.getBirthday().toXMLFormat()),
                admin.getHairColor() == null ? null : ApiModels.Color.valueOf(admin.getHairColor()),
                ApiModels.Country.valueOf(admin.getNationality()), admin.getLocation() == null ? null :
                new ApiModels.Location(admin.getLocation().getX(), admin.getLocation().getY(), admin.getLocation().getZ()));
        ApiModels.StudyGroupRequest request = new ApiModels.StudyGroupRequest(source.getName(),
                new ApiModels.Coordinates(source.getCoordinates().getX(), source.getCoordinates().getY()),
                source.getStudentsCount(), ApiModels.FormOfEducation.valueOf(source.getFormOfEducation()),
                source.getSemesterEnum() == null ? null : ApiModels.Semester.valueOf(source.getSemesterEnum()), person);
        return new ApiModels.StudyGroup(source.getId(), OffsetDateTime.parse(source.getCreationDate().toXMLFormat()).toInstant(), request);
    }

    public static ActionResult toSoap(ApiModels.ActionResult source) {
        ActionResult result = new ActionResult(); result.setCode(source.getCode());
        result.setMessage(source.getMessage()); result.setResourceId(source.getResourceId()); return result;
    }

    public static ApiModels.ActionResult toRest(ActionResult source) {
        return new ApiModels.ActionResult(source.getCode(), source.getMessage(), source.getResourceId());
    }
}
