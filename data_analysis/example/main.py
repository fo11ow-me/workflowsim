import unittest
from src.anova import run


class Main(unittest.TestCase):
    def setUp(self):
        self.base_path = "..\\..\\data\\experiment\\"
        self.output_dir = "..\\..\\data\\anova"

    def test_two_way_anova(self):
        """Test two-way ANOVA with jobSequenceStrategy and neighborhoodFactor"""
        run(
            json_path=self.base_path + "Example06.json",
            target_variable="elecCost",
            group_variables=["workflowComparator", "ascending"],
            output_dir=self.output_dir
        )


if __name__ == '__main__':
    unittest.main()
