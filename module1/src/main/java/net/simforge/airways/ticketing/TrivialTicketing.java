/*
 * Airways Project (c) Alexey Kornev, 2015-2019
 */

package net.simforge.airways.ticketing;

public class TrivialTicketing {
/*    public static List<Flight> search_old2(Connection connx, PassengerGroup group) throws SQLException {
        int fromCityId = group.getPositionCityId();
        int toCityId = !group.isPositionRoundtrip() ? group.getToCityId() : group.getFromCityId();

        Node rootNode = new Node(fromCityId, toCityId, group.getSize());
        rootNode.makeStep(connx);

        List<Node> journeys = rootNode.journeys;
        if (journeys == null || journeys.isEmpty()) {
            return null;
        } else {
            List<Flight> flights = new ArrayList<Flight>();
            Node curr = journeys.get(0);
            while (curr != rootNode) {
                flights.add(0, curr.flight);
                curr = curr.previousNode;
            }
            return flights;
        }
    }

    public static class Node {
        int size;
        int cityId;
        int toCityId;
        Node previousNode;
        Node rootNode;
        Flight flight;
        int stopovers;
        List<Node> nextNodes = new ArrayList<Node>();
        List<Node> journeys;

        public Node(int cityId, int toCityId, int size) {
            this.cityId = cityId;
            this.toCityId = toCityId;
            this.size = size;
            this.rootNode = this;
        }

        public Node(Node previousNode, Flight flight) {
            this.previousNode = previousNode;
            this.flight = flight;
            this.cityId = flight.getToCityId();
            stopovers = previousNode.stopovers + 1;
        }

        public void makeStep(Connection connx) throws SQLException {
            String sql = "select * from %tn% where dep_time > '%dt%' and from_city_id = %from% and free_tickets >= %size% %notInClause% order by dep_time limit 0, 10";
            sql = sql.replaceAll("%dt%", flight == null ? DT.DTF.print(DT.addHours(DT.now(), 1)) : DT.DTF.print(DT.addHours(flight.getArrTime(), 1)));
            sql = sql.replaceAll("%from%", String.valueOf(cityId));
            sql = sql.replaceAll("%size%", String.valueOf(rootNode.size));

            String inClause = "";
            Node curr = this;
            while (curr != rootNode) {
                inClause = inClause + cityId + ",";
                curr = curr.previousNode;
            }
            inClause = inClause + rootNode.cityId + ",";
            if (inClause.endsWith(",")) {
                inClause = inClause.substring(0, inClause.length()-1);
                sql = sql.replace("%notInClause%", "and to_city_id not in (" + inClause + ")");
            } else {
                sql = sql.replace("%notInClause%", "");
            }

            List<Flight> flights = Persistence.loadByQuery(connx, Flight.class, sql);

            for (Flight eachFlight : flights) {
                Node node = new Node(this, eachFlight);
                node.rootNode = rootNode;
                nextNodes.add(node);
            }

            for (Node nextNode : nextNodes) {
                if (nextNode.stopovers > 2) {
                    continue;
                }

                if (rootNode.toCityId == nextNode.cityId) {
                    rootNode.addJourney(nextNode);
                    continue;
                }

                nextNode.makeStep(connx);
            }
        }

        private void addJourney(Node journey) {
            if (journeys == null) {
                journeys = new ArrayList<Node>();
            }
            journeys.add(journey);
        }
    }*/
}
