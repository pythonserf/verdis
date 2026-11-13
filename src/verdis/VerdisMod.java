package verdis;

import arc.util.Log;
import mindustry.mod.Mod;
import verdis.content.VerdisPlanets;
import verdis.content.VerdisSectors;
import verdis.content.VerdisSpace;
import verdis.world.Finale;
import verdis.world.IntroCrash;
import verdis.world.MazeIntro;
import verdis.world.ShipFitting;
import verdis.world.ShipDeck;
import verdis.world.Meteors;

public class VerdisMod extends Mod{

    @Override
    public void loadContent(){
        VerdisPlanets.load();
        VerdisSpace.load();
        VerdisSectors.load();
        Log.info("[Verdis] content loaded");
    }

    @Override
    public void init(){
        IntroCrash.init();
        MazeIntro.init();
        ShipDeck.init();
        ShipFitting.init();
        Meteors.init();
        Finale.init();
    }
}
