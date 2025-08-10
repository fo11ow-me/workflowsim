import unittest
from src.util import anova


class Main(unittest.TestCase):
    def setUp(self):
        self.base_path = "..\\..\\data\\result\\"
        self.output_dir = "..\\..\\data\\anova"

    def test_two_way_anova(self):
        """Test two-way ANOVA with workflowComparator and ascending"""
        anova(
            json_path=self.base_path + "Example06.jsonl",
            target_variable="elecCost",
            group_variables=["workflowComparator", "ascending"],
            output_dir=self.output_dir
        )


if __name__ == '__main__':
    unittest.main()
