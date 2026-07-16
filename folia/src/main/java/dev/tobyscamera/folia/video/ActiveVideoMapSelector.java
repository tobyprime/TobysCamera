package dev.tobyscamera.folia.video;
import java.util.*;
public final class ActiveVideoMapSelector {
 public List<Candidate> select(List<Candidate> candidates,List<Point> players,int limit){return select(candidates,players,limit,Double.POSITIVE_INFINITY);}
 public List<Candidate> select(List<Candidate> candidates,List<Point> players,int limit,double maximumDistanceSquared){if(limit<1||players.isEmpty()||maximumDistanceSquared<0)return List.of();return candidates.stream().filter(c->players.stream().mapToDouble(p->distance(c,p)).min().orElse(Double.MAX_VALUE)<=maximumDistanceSquared).sorted(Comparator.comparingDouble((Candidate c)->players.stream().mapToDouble(p->distance(c,p)).min().orElse(Double.MAX_VALUE)).thenComparingInt(Candidate::mapId)).limit(limit).toList();}
 private static double distance(Candidate c,Point p){if(c.worldId!=null&&p.worldId!=null&&!c.worldId.equals(p.worldId))return Double.MAX_VALUE;double x=c.x-p.x,y=c.y-p.y,z=c.z-p.z;return x*x+y*y+z*z;}
 public record Candidate(int mapId,UUID worldId,double x,double y,double z){public Candidate(int mapId,double x,double y,double z){this(mapId,null,x,y,z);}}
 public record Point(UUID worldId,double x,double y,double z){public Point(double x,double y,double z){this(null,x,y,z);}}
}
