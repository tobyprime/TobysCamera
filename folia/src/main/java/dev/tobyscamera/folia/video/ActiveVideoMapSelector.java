package dev.tobyscamera.folia.video;
import java.util.*;
public final class ActiveVideoMapSelector {
 public List<Candidate> select(List<Candidate> candidates,List<Point> players,int limit){if(limit<1||players.isEmpty())return List.of();return candidates.stream().sorted(Comparator.comparingDouble((Candidate c)->players.stream().mapToDouble(p->distance(c,p)).min().orElse(Double.MAX_VALUE)).thenComparingInt(Candidate::mapId)).limit(limit).toList();}
 private static double distance(Candidate c,Point p){double x=c.x-p.x,y=c.y-p.y,z=c.z-p.z;return x*x+y*y+z*z;}
 public record Candidate(int mapId,double x,double y,double z){} public record Point(double x,double y,double z){}
}
