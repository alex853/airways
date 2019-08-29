package net.simforge.airways.model;

@Deprecated
public interface Airline /*extends BaseEntity, Auditable*/ {
    String getIata();

    void setIata(String iata);

    String getIcao();

    void setIcao(String icao);

    String getName();

    void setName(String name);
}
