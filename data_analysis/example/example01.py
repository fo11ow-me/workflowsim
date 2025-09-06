import os
import unittest
from src.util import anova, compare


class Main(unittest.TestCase):
    def setUp(self):
        self.result_dir = "..\\..\\data\\result\\"
        self.anova_dir = "..\\..\\data\\anova"
        self.comparison_dir = "..\\..\\data\\comparison"
        os.makedirs(self.anova_dir, exist_ok=True)
        os.makedirs(self.comparison_dir, exist_ok=True)

    def test_two_way_anova(self):
        """Test two-way ANOVA with workflowComparator and ascending"""
        anova(
            path=self.result_dir + "Example06.csv",
            group_vars=["workflowComparator", "ascending"],
            output_dir=self.anova_dir
        )

    def test_compare(self):
        compare(path=self.result_dir + "Example07.csv",
                x_axis="workflowComparator",
                output_dir=self.comparison_dir)


if __name__ == '__main__':
    unittest.main()
