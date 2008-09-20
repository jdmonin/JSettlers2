package soc.robot;

import soc.game.SOCBoard;
import soc.game.SOCGame;
import soc.game.SOCPlayer;
import soc.game.SOCResourceConstants;

public class MonopolyStrategy {

	/**
     * this is the resource we want to monopolize
     */
    protected int monopolyChoice;
    
	public boolean decidePlayMonopoly(SOCGame game, SOCPlayer ourPlayerData){
		int bestResourceCount = 0;
        int bestResource = 0;

        for (int resource = SOCResourceConstants.CLAY;
                resource <= SOCResourceConstants.WOOD; resource++)
        {
            //log.debug("$$ resource="+resource);
            int freeResourceCount = 0;
            boolean twoForOne = false;
            boolean threeForOne = false;

            if (ourPlayerData.getPortFlag(resource))
            {
                twoForOne = true;
            }
            else if (ourPlayerData.getPortFlag(SOCBoard.MISC_PORT))
            {
                threeForOne = true;
            }

            int resourceTotal = 0;

            for (int pn = 0; pn < SOCGame.MAXPLAYERS; pn++)
            {
                if (ourPlayerData.getPlayerNumber() != pn)
                {
                    resourceTotal += game.getPlayer(pn).getResources().getAmount(resource);

                    //log.debug("$$ resourceTotal="+resourceTotal);
                }
            }

            if (twoForOne)
            {
                freeResourceCount = resourceTotal / 2;
            }
            else if (threeForOne)
            {
                freeResourceCount = resourceTotal / 3;
            }
            else
            {
                freeResourceCount = resourceTotal / 4;
            }

            //log.debug("freeResourceCount="+freeResourceCount);
            if (freeResourceCount > bestResourceCount)
            {
                bestResourceCount = freeResourceCount;
                bestResource = resource;
            }
        }

        if (bestResourceCount > 2)
        {
            monopolyChoice = bestResource;

            return true;
        }
        else
        {
            return false;
        }
	}

	public int getMonopolyChoice() {
		return monopolyChoice;
	}

	public void setMonopolyChoice(int monopolyChoice) {
		this.monopolyChoice = monopolyChoice;
	}
	
	
}
