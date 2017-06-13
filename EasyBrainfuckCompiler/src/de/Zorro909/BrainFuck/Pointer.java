package de.Zorro909.BrainFuck;

public class Pointer {

    static int currentPointer = 0;
    
    int point = 0;
    public Pointer(int p){
        point = p;
    }
    
    public static void moveTo(Pointer p, BrainFuckScript bfs){
        p.moveTo(bfs);
    }
    
    int cachePoint = -1;
    
    public void moveTo(BrainFuckScript bfs){
        cachePoint = currentPointer - point;
        currentPointer = point;
        if(cachePoint < 0){
            cachePoint *= -1;
            for(int i = 0;i<cachePoint;i++){
                bfs.script += "<";
            }
        }else if(cachePoint > 0){
            for(int i = 0;i<cachePoint;i++){
                bfs.script += ">";
            }
        }
    }
    
}
