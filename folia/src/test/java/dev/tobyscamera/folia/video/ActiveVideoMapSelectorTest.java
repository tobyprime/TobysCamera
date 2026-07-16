package dev.tobyscamera.folia.video;
import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List; import org.junit.jupiter.api.Test;
class ActiveVideoMapSelectorTest { @Test void selectsNearestIndividualMapsUpToBudget() { var maps=List.of(new ActiveVideoMapSelector.Candidate(3,9,0,0),new ActiveVideoMapSelector.Candidate(1,1,0,0),new ActiveVideoMapSelector.Candidate(2,4,0,0)); assertEquals(List.of(1,2),new ActiveVideoMapSelector().select(maps,List.of(new ActiveVideoMapSelector.Point(0,0,0)),2).stream().map(ActiveVideoMapSelector.Candidate::mapId).toList()); } }
