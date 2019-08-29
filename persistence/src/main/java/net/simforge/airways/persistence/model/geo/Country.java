package net.simforge.airways.persistence.model.geo;

import javax.persistence.*;

@Entity
@Table(name = "aw_country")
public class Country {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aw_country_id_seq")
    @SequenceGenerator(name = "aw_country_id_seq", sequenceName = "aw_country_id_seq", allocationSize = 1)
    private Integer id;
    @Version
    private Integer version;

    @Column
    private String name;
    @Column
    private String code;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

}
