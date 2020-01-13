/*
 Game board object, for geometry and other methods; loaded from game-ui.js.
 Some methods are from SOCBoard.java or SOCBoardLarge.java.

 This file is part of the Java Settlers Web App (JSWeb).

 This file Copyright (C) 2020 Jeremy D Monin (jeremy@nand.net)

 JSWeb is free software: you can redistribute it and/or modify
 it under the terms of the GNU Affero General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 JSWeb is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU Affero General Public License for more details.

 You should have received a copy of the GNU Affero General Public License
 along with JSWeb.  If not, see <https://www.gnu.org/licenses/>.
 */

class Board
{
	constructor(h, w)
	{
		this.height = h;
		this.width = w;
	}

	/** The hex touching an edge in a given direction, either along its length or at one end node. Each edge touches up to 4 valid hexes. */
	getAdjacentHexToEdge(edge, facing)
	{
		/** port facings. Facing is the direction (1-6) to the hex touching a hex or edge,
		 * or from a node to another node 2 nodes away. Used with port edges.
		 * Facing 1 is NE, 2 is E, 3 is SE, 4 is SW, etc.
		 */
		const FACING_NE = 1, FACING_E = 2, FACING_SE = 3, FACING_SW = 4, FACING_W = 5, FACING_NW = 6;  // TODO declare these constants once, at higher level

		let r = edge >> 8, c = edge & 0xFF;

		// "|" if r is odd
		if ((r%2) == 1)
		{
			switch (facing)
			{
			case FACING_E:
				++c;
				break;
			case FACING_W:
				--c;
				break;
			case FACING_NE: case FACING_NW:
				r = r - 2;
				break;
			case FACING_SE: case FACING_SW:
				r = r + 2;
				break;
			default:
				return -1;
			}
		}

		// "/" if (s,c) is even,odd or odd,even
		else if ((c % 2) != ((r/2) % 2))
		{
			switch (facing)
			{
			case FACING_NW:
				--r;
				break;
			case FACING_SE:
				++r;
				++c;
				break;
			case FACING_NE: case FACING_E:
				--r;
				c = c + 2;
				break;
			case FACING_SW: case FACING_W:
				++r;
				--c;
				break;
			default:
				return -1;
			}
		}
		else
		{
			// "\" if (s,c) is odd,odd or even,even
			switch (facing)
			{
			case FACING_NE:
				--r;
				++c;
				break;
			case FACING_SW:
				++r;
				break;
			case FACING_E: case FACING_SE:
				++r;
				c = c + 2;
				break;
			case FACING_W: case FACING_NW:
				--r;
				--c;
				break;
			default:
				return -1;
			}
		}

		if ((r > 0) && (c > 0) && (r < this.height) && (c < this.width))
			return ( (r << 8) | c );   // bounds-check OK: within the outer edge
		else
			return 0;  // hex is not on the board
	}
}
