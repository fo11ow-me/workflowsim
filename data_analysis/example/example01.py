import unittest
from src.util import anova, plot_comparison_chart


class Main(unittest.TestCase):
    def setUp(self):
        self.result_dir = "..\\..\\data\\result\\"
        self.anova_dir = "..\\..\\data\\anova"
        self.comparison_dir = "..\\..\\data\\comparison"


    def test_two_way_anova(self):
        """Test two-way ANOVA with workflowComparator and ascending"""
        anova(
            path=self.result_dir + "Example06.csv",
            target_variable="elecCost",
            group_variables=["workflowComparator", "ascending"],
            output_dir=self.anova_dir
        )

    def test_comparison(self):
        plot_comparison_chart(self.result_dir + "Example07.csv", x_axis="workflowComparator",
                              output_dir=self.comparison_dir)


if __name__ == '__main__':
    unittest.main()
