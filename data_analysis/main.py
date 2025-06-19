import unittest
from anova import run_anova


class Main(unittest.TestCase):
    def setUp(self):
        self.base_path = "..\\data\\experiment\\"

    def test_one_way_anova(self):
        """Test one-way ANOVA with jobSequenceStrategy"""
        run_anova(
            json_path=self.base_path + "Demo10.json",
            target_variable="elecCost",
            group_variables=["jobSequenceStrategy"]
        )

    def test_two_way_anova(self):
        """Test two-way ANOVA with jobSequenceStrategy and neighborhoodFactor"""
        run_anova(
            json_path=self.base_path + "Demo14.json",
            target_variable="elecCost",
            group_variables=["jobSequenceStrategy", "neighborhoodFactor"]
        )

    def test_three_way_anova(self):
        """Test three-way ANOVA with jobSequenceStrategy, neighborhoodFactor and slackTimeFactor"""
        run_anova(
            json_path=self.base_path + "Demo15.json",
            target_variable="elecCost",
            group_variables=["jobSequenceStrategy", "neighborhoodFactor", "slackTimeFactor"]
        )


if __name__ == '__main__':
    unittest.main()
