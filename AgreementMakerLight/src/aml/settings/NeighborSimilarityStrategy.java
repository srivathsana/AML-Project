/******************************************************************************
* Copyright 2013-2015 LASIGE                                                  *
*                                                                             *
* Licensed under the Apache License, Version 2.0 (the "License"); you may     *
* not use this file except in compliance with the License. You may obtain a   *
* copy of the License at http://www.apache.org/licenses/LICENSE-2.0           *
*                                                                             *
* Unless required by applicable law or agreed to in writing, software         *
* distributed under the License is distributed on an "AS IS" BASIS,           *
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.    *
* See the License for the specific language governing permissions and         *
* limitations under the License.                                              *
*                                                                             *
*******************************************************************************
* Lists the NeighborSimilarityMatcher strategy options.                       *
*                                                                             *
* @author Daniel Faria                                                        *
* @date 11-09-2014                                                            *
******************************************************************************/
package aml.settings;

public enum NeighborSimilarityStrategy
{
	ANCESTORS ("Ancestors"),
	DESCENDANTS ("Descendants"),
	AVERAGE ("Average"),
	MAXIMUM ("Maximum"),
	MINIMUM ("Minimum");
	
	String label;
	
	NeighborSimilarityStrategy(String s)
    {
    	label = s;
    }
	
	public static NeighborSimilarityStrategy parseStrategy(String strat)
	{
		for(NeighborSimilarityStrategy s : NeighborSimilarityStrategy.values())
			if(strat.equalsIgnoreCase(s.toString()))
				return s;
		return null;
	}
	
    public String toString()
    {
    	return label;
	}
}